package org.kde.kdeconnect.Backends.CustomLinkBackend;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.support.v4.util.LongSparseArray;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioDatagramAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;

public class CustomLinkProvider extends BaseLinkProvider {

    private final static int port = 1714;

    private final Context context;
    private final HashMap<String, CustomLink> visibleComputers = new HashMap<String, CustomLink>();
    private final LongSparseArray<CustomLink> nioSessions = new LongSparseArray<CustomLink>();

    private NioSocketAcceptor tcpAcceptor = null;
    private NioDatagramAcceptor udpAcceptor = null;

    private final IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionClosed(IoSession session) throws Exception {

            CustomLink brokenLink = nioSessions.get(session.getId());
            if (brokenLink != null) {
                nioSessions.remove(session.getId());
                connectionLost(brokenLink);
                brokenLink.disconnect();
                String deviceId = brokenLink.getDeviceId();
                if (visibleComputers.get(deviceId) == brokenLink) {
                    visibleComputers.remove(deviceId);
                }
            }

        }

        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {
            super.messageReceived(session, message);

            //Log.e("CustomLinkProvider","Incoming package, address: "+session.getRemoteAddress()).toString());
            //Log.e("CustomLinkProvider","Received:"+message);

            String theMessage = (String) message;
            if (theMessage.isEmpty()) {
                Log.e("CustomLinkProvider","Empty package received");
                return;
            }

            NetworkPackage np = NetworkPackage.unserialize(theMessage);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

                String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                if (np.getString("deviceId").equals(myId)) {
                    return;
                }

                //Log.e("CustomLinkProvider", "Identity package received from "+np.getString("deviceName"));

                CustomLink link = new CustomLink(session, np.getString("deviceId"), CustomLinkProvider.this);
                nioSessions.put(session.getId(),link);
                addLink(np, link);
            } else {
                CustomLink prevLink = nioSessions.get(session.getId());
                if (prevLink == null) {
                    Log.e("CustomLinkProvider","2 Expecting an identity package");
                } else {
                    prevLink.injectNetworkPackage(np);
                }
            }

        }
    };

    private final IoHandler udpHandler = new IoHandlerAdapter() {
        @Override
        public void messageReceived(IoSession udpSession, Object message) throws Exception {
            super.messageReceived(udpSession, message);

            //Log.e("CustomLinkProvider", "Udp message received (" + message.getClass() + ") " + message.toString());

            try {
                //We should receive a string thanks to the TextLineCodecFactory filter
                String theMessage = (String) message;
                final NetworkPackage identityPackage = NetworkPackage.unserialize(theMessage);

                if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                    Log.e("CustomLinkProvider", "1 Expecting an identity package");
                    return;
                } else {
                    String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                    if (identityPackage.getString("deviceId").equals(myId)) {
                        return;
                    }
                }

                Log.i("CustomLinkProvider", "Identity package received, creating link");

                final InetSocketAddress address = (InetSocketAddress) udpSession.getRemoteAddress();

                final NioSocketConnector connector = new NioSocketConnector();
                connector.setHandler(tcpHandler);
                //TextLineCodecFactory will split incoming data delimited by the given string
                connector.getFilterChain().addLast("codec",
                        new ProtocolCodecFilter(
                                new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                        )
                );
                connector.getSessionConfig().setKeepAlive(true);

                int tcpPort = identityPackage.getInt("tcpPort",port);
                ConnectFuture future = connector.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                future.addListener(new IoFutureListener<IoFuture>() {
                    @Override
                    public void operationComplete(IoFuture ioFuture) {
                        final IoSession session = ioFuture.getSession();

                        final CustomLink link = new CustomLink(session, identityPackage.getString("deviceId"), CustomLinkProvider.this);

                        Log.i("CustomLinkProvider", "Connection successful: " + session.isConnected());

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                NetworkPackage np2 = NetworkPackage.createIdentityPackage(context);
                                link.sendPackage(np2);

                                nioSessions.put(session.getId(), link);
                                addLink(identityPackage, link);
                            }
                        }).start();

                    }
                });

            } catch (Exception e) {
                Log.e("CustomLinkProvider","Exception receiving udp package!!");
                e.printStackTrace();
            }

        }
    };

    private void addLink(NetworkPackage identityPackage, CustomLink link) {
        String deviceId = identityPackage.getString("deviceId");
        Log.i("CustomLinkProvider","addLink to "+deviceId);
        CustomLink oldLink = visibleComputers.get(deviceId);
        visibleComputers.put(deviceId, link);
        connectionAccepted(identityPackage, link);
        if (oldLink != null) {
            Log.i("CustomLinkProvider","Removing old connection to same device");
            oldLink.disconnect();
            connectionLost(oldLink);
        }
    }

    public CustomLinkProvider(Context context) {

        this.context = context;

        //This handles the case when I'm the new device in the network and somebody answers my introduction package
        tcpAcceptor = new NioSocketAcceptor();
        tcpAcceptor.setHandler(tcpHandler);
        tcpAcceptor.getSessionConfig().setKeepAlive(true);
        tcpAcceptor.getSessionConfig().setReuseAddress(true);
        tcpAcceptor.setCloseOnDeactivation(false);
        //TextLineCodecFactory will split incoming data delimited by the given string
        tcpAcceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                )
        );


        udpAcceptor = new NioDatagramAcceptor();
        udpAcceptor.getSessionConfig().setReuseAddress(true);        //Share port if existing
        //TextLineCodecFactory will split incoming data delimited by the given string
        udpAcceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(Charset.defaultCharset(), LineDelimiter.UNIX, LineDelimiter.UNIX)
                )
        );

    }

    @Override
    public void onStart() {

        //This handles the case when I'm the existing device in the network and receive a "hello" UDP package
        // This part is probably unnecessary now, since this Provider doesn't rely on broadcast packages

        /*
        udpAcceptor.setHandler(udpHandler);

        try {
            udpAcceptor.bind(new InetSocketAddress(port));
        } catch(Exception e) {
            Log.e("CustomLinkProvider", "Error: Could not bind udp socket");
            e.printStackTrace();
        }
        */

        boolean success = false;
        int tcpPort = port;
        while(!success) {
            try {
                tcpAcceptor.bind(new InetSocketAddress(tcpPort));
                success = true;
            } catch(Exception e) {
                tcpPort++;
            }
        }

        Log.i("CustomLinkProvider","Using tcpPort "+tcpPort);


        //I'm on a new network, let's be polite and introduce myself
        // In this case, we'll just introduce ourselves to a specific list of IP addresses
        final int finalTcpPort = tcpPort;
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    ArrayList<InetAddress> clientlist = new ArrayList<InetAddress>();
                    clientlist.add(InetAddress.getByAddress(new byte[]{10, 8, 0, 13}));
                    clientlist.add(InetAddress.getByAddress(new byte[]{10, 8, 0, 100}));
                    for (InetAddress client : clientlist) {

                        NetworkPackage identity = NetworkPackage.createIdentityPackage(context);
                        identity.set("tcpPort", finalTcpPort);
                        byte[] b = identity.serialize().getBytes("UTF-8");
                        DatagramPacket packet = new DatagramPacket(b, b.length, client, port);
                        DatagramSocket socket = new DatagramSocket();
                        socket.setReuseAddress(true);
                        socket.setBroadcast(true);
                        socket.send(packet);
                        Log.e("CustomLinkProvider","Udp identity package sent to address "+packet.getAddress());
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("CustomLinkProvider","Sending udp identity package failed");
                }

                return null;

            }

        }.execute();

    }

    @Override
    public void onNetworkChange() {

        onStop();
        onStart();

    }

    @Override
    public void onStop() {

        udpAcceptor.unbind();
        tcpAcceptor.unbind();

    }

    public int getPriority() {
        return 1000;
    }

    @Override
    public String getName() {
        return "CustomLinkProvider";
    }
}
