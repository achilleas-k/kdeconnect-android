package org.kde.kdeconnect.Backends.VpnBackend;

import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.util.LongSparseArray;
import android.util.Log;

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
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.HashMap;

public class VpnLinkProvider extends BaseLinkProvider {

    private final static int port = 1714;

    private final Context context;
    private final HashMap<String, VpnLink> visibleComputers = new HashMap<String, VpnLink>();
    private final LongSparseArray<VpnLink> nioSessions = new LongSparseArray<VpnLink>();

    private NetworkInterface networkInterface = null;
    private NioSocketAcceptor tcpAcceptor = null;
    private NioDatagramAcceptor udpAcceptor = null;

    private final IoHandler tcpHandler = new IoHandlerAdapter() {
        @Override
        public void sessionClosed(IoSession session) throws Exception {

            VpnLink brokenLink = nioSessions.get(session.getId());
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

            //Log.e("VpnLinkProvider","Incoming package, address: "+session.getRemoteAddress()).toString());
            //Log.e("VpnLinkProvider","Received:"+message);

            String theMessage = (String) message;
            if (theMessage.isEmpty()) {
                Log.e("VpnLinkProvider","Empty package received");
                return;
            }

            NetworkPackage np = NetworkPackage.unserialize(theMessage);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {

                String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                if (np.getString("deviceId").equals(myId)) {
                    return;
                }

                //Log.e("VpnLinkProvider", "Identity package received from "+np.getString("deviceName"));

                VpnLink link = new VpnLink(session, np.getString("deviceId"), VpnLinkProvider.this);
                nioSessions.put(session.getId(), link);
                addLink(np, link);
            } else {
                VpnLink prevLink = nioSessions.get(session.getId());
                if (prevLink == null) {
                    Log.e("VpnLinkProvider","2 Expecting an identity package");
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

            //Log.e("VpnLinkProvider", "Udp message received (" + message.getClass() + ") " + message.toString());

            try {
                //We should receive a string thanks to the TextLineCodecFactory filter
                String theMessage = (String) message;
                final NetworkPackage identityPackage = NetworkPackage.unserialize(theMessage);

                if (!identityPackage.getType().equals(NetworkPackage.PACKAGE_TYPE_IDENTITY)) {
                    Log.e("VpnLinkProvider", "1 Expecting an identity package");
                    return;
                } else {
                    String myId = NetworkPackage.createIdentityPackage(context).getString("deviceId");
                    if (identityPackage.getString("deviceId").equals(myId)) {
                        return;
                    }
                }

                Log.i("VpnLinkProvider", "Identity package received, creating link");

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

                        final VpnLink link = new VpnLink(session, identityPackage.getString("deviceId"), VpnLinkProvider.this);

                        Log.i("VpnLinkProvider", "Connection successful: " + session.isConnected());

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
                Log.e("VpnLinkProvider","Exception receiving udp package!!");
                e.printStackTrace();
            }

        }
    };

    private void addLink(NetworkPackage identityPackage, VpnLink link) {
        String deviceId = identityPackage.getString("deviceId");
        Log.i("VpnLinkProvider","addLink to "+deviceId);
        VpnLink oldLink = visibleComputers.get(deviceId);
        visibleComputers.put(deviceId, link);
        connectionAccepted(identityPackage, link);
        if (oldLink != null) {
            Log.i("VpnLinkProvider","Removing old connection to same device");
            oldLink.disconnect();
            connectionLost(oldLink);
        }
    }

    public VpnLinkProvider(Context context, NetworkInterface networkInterface) {

        this.context = context;
        this.networkInterface = networkInterface;

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

        udpAcceptor.setHandler(udpHandler);
        InetAddress deviceVpnIp = networkInterface.getInterfaceAddresses().get(0).getAddress();

        try {
            udpAcceptor.bind(new InetSocketAddress(deviceVpnIp, port));
        } catch(Exception e) {
            Log.e("VpnLinkProvider", "Error: Could not bind udp socket");
            e.printStackTrace();
        }

        boolean success = false;
        int tcpPort = port;
        while(!success) {
            try {
                tcpAcceptor.bind(new InetSocketAddress(deviceVpnIp, tcpPort));
                success = true;
            } catch(Exception e) {
                tcpPort++;
            }
        }

        Log.i("VpnLinkProvider","Using tcpPort "+tcpPort);

        //I'm on a new network, let's be polite and introduce myself
        final int finalTcpPort = tcpPort;
        new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    NetworkPackage identity = NetworkPackage.createIdentityPackage(context);
                    identity.set("tcpPort",finalTcpPort);
                    byte[] b = identity.serialize().getBytes("UTF-8");
                    DatagramPacket packet = new DatagramPacket(b, b.length, InetAddress.getByAddress(new byte[]{10,8,0,13}), port);
                    DatagramSocket socket = new DatagramSocket();
                    socket.setReuseAddress(true);
                    socket.setBroadcast(true);
                    socket.send(packet);
                    Log.e("VpnLinkProvider","Udp identity package sent "+packet.getAddress());
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("VpnLinkProvider","Sending udp identity package failed");
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

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public String getName() {
        return "VpnLinkProvider";
    }
}
