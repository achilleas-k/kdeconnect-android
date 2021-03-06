/*
 * Copyright 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.kde.kdeconnect;

import android.test.AndroidTestCase;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.kde.kdeconnect.Backends.LanBackend.LanLink;
import org.kde.kdeconnect.Backends.LanBackend.LanLinkProvider;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

class LanLinkTest extends AndroidTestCase {

    private LanLink badLanLink;
    private LanLink goodLanLink;

    private OutputStream badOutputStream;
    private OutputStream goodOutputStream;

    private Device.SendPacketStatusCallback callback;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        LanLinkProvider linkProvider = Mockito.mock(LanLinkProvider.class);
        Mockito.when(linkProvider.getName()).thenReturn("LanLinkProvider");

        callback = Mockito.mock(Device.SendPacketStatusCallback.class);

        goodOutputStream = Mockito.mock(OutputStream.class);
        badOutputStream = Mockito.mock(OutputStream.class);
        Mockito.doThrow(new IOException("AAA")).when(badOutputStream).write(Mockito.any(byte[].class));


        Socket socketMock = Mockito.mock(Socket.class);
        Mockito.when(socketMock.getRemoteSocketAddress()).thenReturn(new InetSocketAddress(5000));
        Mockito.when(socketMock.getOutputStream()).thenReturn(goodOutputStream);

        Socket socketBadMock = Mockito.mock(Socket.class);
        Mockito.when(socketBadMock.getRemoteSocketAddress()).thenReturn(new InetSocketAddress(5000));
        Mockito.when(socketBadMock.getOutputStream()).thenReturn(badOutputStream);

        goodLanLink = new LanLink(getContext(), "testDevice", linkProvider, socketMock, LanLink.ConnectionStarted.Remotely);
        badLanLink = new LanLink(getContext(), "testDevice", linkProvider, socketBadMock, LanLink.ConnectionStarted.Remotely);
    }

    public void testSendPacketSuccess() throws JSONException {

        NetworkPacket testPacket = Mockito.mock(NetworkPacket.class);
        Mockito.when(testPacket.getType()).thenReturn("kdeconnect.test");
        Mockito.when(testPacket.getBoolean("isTesting")).thenReturn(true);
        Mockito.when(testPacket.getString("testName")).thenReturn("testSendPacketSuccess");
        Mockito.when(testPacket.serialize()).thenReturn("{\"id\":123,\"type\":\"kdeconnect.test\",\"body\":{\"isTesting\":true,\"testName\":\"testSendPacketSuccess\"}}");

        goodLanLink.sendPacket(testPacket, callback);

        Mockito.verify(callback).onSuccess();
    }

    public void testSendPacketFail() throws JSONException {

        NetworkPacket testPacket = Mockito.mock(NetworkPacket.class);
        Mockito.when(testPacket.getType()).thenReturn("kdeconnect.test");
        Mockito.when(testPacket.getBoolean("isTesting")).thenReturn(true);
        Mockito.when(testPacket.getString("testName")).thenReturn("testSendPacketFail");
        Mockito.when(testPacket.serialize()).thenReturn("{\"id\":123,\"type\":\"kdeconnect.test\",\"body\":{\"isTesting\":true,\"testName\":\"testSendPacketFail\"}}");

        badLanLink.sendPacket(testPacket, callback);

        Mockito.verify(callback).onFailure(Mockito.any(RuntimeException.class));

    }


    public void testSendPayload() throws Exception {

        class Downloader extends Thread {

            NetworkPacket np;
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            void setNetworkPacket(NetworkPacket networkPacket) {
                this.np = networkPacket;
            }

            ByteArrayOutputStream getOutputStream() {
                return outputStream;
            }

            @Override
            public void run() {
                try {

                    Socket socket = null;
                    try {
                        socket = new Socket();
                        int tcpPort = np.getPayloadTransferInfo().getInt("port");
                        InetSocketAddress address = new InetSocketAddress(5000);
                        socket.connect(new InetSocketAddress(address.getAddress(), tcpPort));
                        np.setPayload(socket.getInputStream(), np.getPayloadSize());
                    } catch (Exception e) {
                        socket.close();
                        e.printStackTrace();
                        Log.e("KDE/LanLinkTest", "Exception connecting to remote socket");
                        throw e;
                    }

                    final InputStream input = np.getPayload();
                    final long fileLength = np.getPayloadSize();

                    byte data[] = new byte[1024];
                    long progress = 0, prevProgressPercentage = 0;
                    int count;
                    while ((count = input.read(data)) >= 0) {
                        progress += count;
                        outputStream.write(data, 0, count);
                        if (fileLength > 0) {
                            if (progress >= fileLength) break;
                            long progressPercentage = (progress * 100 / fileLength);
                            if (progressPercentage != prevProgressPercentage) {
                                prevProgressPercentage = progressPercentage;
                            }
                        }

                    }
                    outputStream.close();
                    input.close();

                } catch (Exception e) {
                    Log.e("Downloader Test", "Exception");
                    e.printStackTrace();
                }
            }
        }


        final Downloader downloader = new Downloader();

        // Using byte array for payload, try to use input stream as used in real device
        String dataString = "Lorem ipsum dolor sit amet, consectetur adipiscing elit." +
                " Cras vel erat et ante fringilla tristique. Sed consequat ligula at interdum " +
                "rhoncus. Integer semper enim felis, id sodales tellus aliquet eget." +
                " Sed fringilla ac metus eget dictum. Aliquam euismod non sem sit" +
                " amet dapibus. Interdum et malesuada fames ac ante ipsum primis " +
                "in faucibus. Nam et ligula placerat, varius justo eu, convallis " +
                "lorem. Nam consequat consequat tortor et gravida. Praesent " +
                "ultricies tortor eget ex elementum gravida. Suspendisse aliquet " +
                "erat a orci feugiat dignissim.";

        // reallyLongString contains dataString 16 times
        String reallyLongString = dataString + dataString;
        reallyLongString = reallyLongString + reallyLongString;
        reallyLongString = reallyLongString + reallyLongString;
        reallyLongString = reallyLongString + reallyLongString;

        final byte[] data = reallyLongString.getBytes();

        final JSONObject sharePacketJson = new JSONObject("{\"id\":123,\"body\":{\"filename\":\"data.txt\"},\"payloadTransferInfo\":{},\"payloadSize\":8720,\"type\":\"kdeconnect.share\"}");

        // Mocking share package
        final NetworkPacket sharePacket = Mockito.mock(NetworkPacket.class);
        Mockito.when(sharePacket.getType()).thenReturn("kdeconnect.share");
        Mockito.when(sharePacket.hasPayload()).thenReturn(true);
        Mockito.when(sharePacket.hasPayloadTransferInfo()).thenReturn(true);
        Mockito.doAnswer(invocationOnMock -> sharePacketJson.toString()).when(sharePacket).serialize();
        Mockito.when(sharePacket.getPayload()).thenReturn(new ByteArrayInputStream(data));
        Mockito.when(sharePacket.getPayloadSize()).thenReturn((long) data.length);
        Mockito.doAnswer(invocationOnMock -> sharePacketJson.getJSONObject("payloadTransferInfo")).when(sharePacket).getPayloadTransferInfo();
        Mockito.doAnswer(invocationOnMock -> {
            JSONObject object = (JSONObject) invocationOnMock.getArguments()[0];

            sharePacketJson.put("payloadTransferInfo", object);
            return null;
        }).when(sharePacket).setPayloadTransferInfo(Mockito.any(JSONObject.class));

        Mockito.doAnswer(invocationOnMock -> {

            Log.e("LanLinkTest", "Write to stream");
            String stringNetworkPacket = new String((byte[]) invocationOnMock.getArguments()[0]);
            final NetworkPacket np = NetworkPacket.unserialize(stringNetworkPacket);

            downloader.setNetworkPacket(np);
            downloader.start();

            return stringNetworkPacket.length();
        }).when(goodOutputStream).write(Mockito.any(byte[].class));

        goodLanLink.sendPacket(sharePacket, callback);

        try {
            // Wait 1 secs for downloader to finish (if some error, it will continue and assert will fail)
            downloader.join(1000);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        assertEquals(new String(data), new String(downloader.getOutputStream().toByteArray()));

        Mockito.verify(callback).onSuccess();

    }
}
