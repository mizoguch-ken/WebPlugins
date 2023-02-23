/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.event.EventListenerList;

/**
 *
 * @author mizoguch-ken
 */
public final class LinkBoxClient {

    private final EventListenerList eventListenerList_;

    private InetAddress serverAddress_;
    private InetSocketAddress inetSocketAddress_;
    private Socket socket_;
    private BufferedReader bufferedReader_;
    private PrintWriter printWriter_;

    private int clientPort_;
    private int clientTimeout_;

    private boolean isRunning_;

    /**
     *
     */
    public LinkBoxClient() {
        eventListenerList_ = new EventListenerList();
        serverAddress_ = null;
        isRunning_ = false;
    }

    /**
     *
     * @param listener
     */
    public void addLinkBoxClientListener(LinkBoxClientListener listener) {
        boolean isListener = false;
        for (LinkBoxClientListener lbsl : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
            if (lbsl == listener) {
                isListener = true;
                break;
            }
        }
        if (!isListener) {
            eventListenerList_.add(LinkBoxClientListener.class, listener);
        }
    }

    /**
     *
     * @param listener
     */
    public void removeLinkBoxClientListener(LinkBoxClientListener listener) {
        eventListenerList_.remove(LinkBoxClientListener.class, listener);
    }

    /**
     *
     * @return
     */
    public boolean isRunning() {
        return isRunning_;
    }

    /**
     *
     * @param address
     * @return
     */
    public boolean setServerAddress(InetAddress address) {
        serverAddress_ = address;
        return true;
    }

    /**
     *
     * @param address
     * @return
     */
    public boolean setServerAddress(String address) {
        try {
            serverAddress_ = InetAddress.getByName(address);
            return true;
        } catch (UnknownHostException ex) {
            exceptionCaught(ex);
        }
        return false;
    }

    /**
     *
     * @param address1
     * @param address2
     * @param address3
     * @param address4
     * @return
     */
    public boolean setServerAddress(byte address1, byte address2, byte address3, byte address4) {
        try {
            serverAddress_ = InetAddress.getByAddress(new byte[]{address1, address2, address3, address4});
            return true;
        } catch (UnknownHostException ex) {
            exceptionCaught(ex);
        }
        return false;
    }

    /**
     *
     * @return
     */
    public int getConnectionPort() {
        return clientPort_;
    }

    /**
     *
     * @param port
     */
    public void setConnectionPort(int port) {
        clientPort_ = port;
    }

    /**
     *
     * @param timeout
     */
    public void setTimeout(int timeout) {
        clientTimeout_ = timeout;
    }

    /**
     * ポートの接続状態を強制的に初期化します。
     *
     * @return
     */
    public boolean cmdPortClose() {
        return cmdPortClose(0);
    }

    /**
     *
     * @param port
     * @return
     */
    public boolean cmdPortClose(int port) {
        InetSocketAddress inetSocketAddress;
        Socket socket = new Socket();
        BufferedReader bufferedReader = null;
        PrintWriter printWriter = null;
        StringBuilder request = new StringBuilder();
        boolean result = false;

        try {
            inetSocketAddress = new InetSocketAddress(serverAddress_, 50099);
            socket.connect(inetSocketAddress, 5000);
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8")));

            request.append("PortClose");
            if (port > 50000) {
                request.append(Integer.toString(port));
            }

            result = checkErrorCode(sendRequest("PortClose", request.toString(), 5000 + clientTimeout_, 0, socket, bufferedReader, printWriter));
        } catch (IOException ex) {
            exceptionCaught(ex);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    exceptionCaught(ex);
                }
            }

            if (printWriter != null) {
                printWriter.close();
            }

            try {
                socket.close();
            } catch (IOException ex) {
                exceptionCaught(ex);
            }
        }
        return result;
    }

    /**
     * Link Box IIIへの制御用コンピュータの接続状態を取得します。
     *
     * @param port
     * @return
     */
    public boolean cmdGetPortInfo(int port) {
        InetSocketAddress inetSocketAddress;
        Socket socket = new Socket();
        BufferedReader bufferedReader = null;
        PrintWriter printWriter = null;
        StringBuilder request = new StringBuilder();
        boolean result = false;

        try {
            inetSocketAddress = new InetSocketAddress(serverAddress_, 50099);
            socket.connect(inetSocketAddress, 5000);
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8")));

            request.append("GetPortInfo").append(Integer.toString(port));
            result = checkErrorCode(sendRequest("GetPortInfo", request.toString(), clientTimeout_, 0, socket, bufferedReader, printWriter));
        } catch (IOException ex) {
            exceptionCaught(ex);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    exceptionCaught(ex);
                }
            }

            if (printWriter != null) {
                printWriter.close();
            }

            try {
                socket.close();
            } catch (IOException ex) {
                exceptionCaught(ex);
            }
        }
        return result;
    }

    /**
     * 作業指示用ソケット ソケットの向きはLink Box III側から見て制御用コンピュータがクライアントとなります。 制御用コンピュータ側
     * クライアントソケット(任意の空きポート) Link Box III側 サーバーソケット(50011番∼50014番ポート)
     *
     * @return
     */
    public boolean cmdConnect() {
        InetSocketAddress inetSocketAddress;
        Socket socket = new Socket();
        BufferedReader bufferedReader = null;
        boolean result = false;

        try {
            inetSocketAddress = new InetSocketAddress(serverAddress_, 50001);
            socket.connect(inetSocketAddress, 30000);
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

            result = checkErrorCode(sendRequest("Connect", null, clientTimeout_, 1000, socket, bufferedReader, null));
        } catch (IOException ex) {
            exceptionCaught(ex);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    exceptionCaught(ex);
                }
            }

            try {
                socket.close();
            } catch (IOException ex) {
                exceptionCaught(ex);
            }
        }
        return result;
    }

    /**
     * Link Box IIIとの接続を切断します。
     *
     * @return
     */
    public boolean cmdDisconnect() {
        return checkErrorCode(sendRequest("Disconnect", "Disconnect", clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 当該Link Box IIIに接続されている全ての機器の状態を初期化します。
     *
     * @return
     */
    public boolean cmdInitAK() {
        return checkErrorCode(sendRequest("InitAK", "InitAK", 100 + clientTimeout_, 100, socket_, bufferedReader_, printWriter_));
    }

    /**
     * メンテナンスモード指定。機器が全点灯またはアドレス表示します。 アプリケーションの装置点検機能に実装してください。
     * 業務稼動中には通常使用しません。
     *
     * @param ch
     * @return
     */
    public boolean cmdMnt(int ch) {
        StringBuilder request = new StringBuilder();

        request.append("Mnt");
        if (ch > 0) {
            request.append(ch);
        }
        return checkErrorCode(sendRequest("Mnt", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     *
     * @return
     */
    public boolean cmdMnt() {
        return cmdMnt(0);
    }

    /**
     * 機器にアドレスを設定します。 アプリケーションから機器アドレスの設定を行なう場合は、装置点検メニューに実装してください。
     * アドレスライターと同様の機能です。
     *
     * @param unit
     * @return
     */
    public boolean cmdAddrAK(int unit) {
        StringBuilder request = new StringBuilder();

        request.append("AddrAK").append(String.format("%04d", unit));
        return checkErrorCode(sendRequest("AddrAK", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 自動応答(イベントドリブン)モードを開始します。 自動応答モードを開始すると、Link Box
     * IIIは作業中アンサーキットに作業完了などのイベントが発生したときに、 制御用コンピュータに自動的に作業結果を応答します。
     *
     * @return
     */
    public boolean cmdSetAutoATT() {
        return checkErrorCode(sendRequest("SetAutoATT", "SetAutoATT", clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 自動応答モードを解除します
     *
     * @return
     */
    public boolean cmdClearAutoATT() {
        return checkErrorCode(sendRequest("ClearAutoATT", "ClearAutoATT", clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 表示モード(Set2AK1,2)を付けてアンサーキットに作業指示をします(ACCNo.の指定 有効)。
     * SetLightAK1と同様の機能でパラメータをASCIIで指定可能。最大128台のアンサーキットを指定可能です。
     *
     * @param accNumber
     * @param normalDirection
     * @param normalLedR
     * @param normalLedG
     * @param normalLedB
     * @param normalSeg
     * @param normalBuz
     * @param answerDirection
     * @param answerLedR
     * @param answerLedG
     * @param answerLedB
     * @param answerSeg
     * @param answerBuz
     * @param unit
     * @param view
     * @return
     */
    public boolean cmdSetL1(
            int accNumber,
            LinkBoxEnums normalDirection,
            LinkBoxEnums normalLedR, LinkBoxEnums normalLedG, LinkBoxEnums normalLedB,
            LinkBoxEnums normalSeg, LinkBoxEnums normalBuz,
            LinkBoxEnums answerDirection,
            LinkBoxEnums answerLedR, LinkBoxEnums answerLedG, LinkBoxEnums answerLedB,
            LinkBoxEnums answerSeg, LinkBoxEnums answerBuz,
            int unit, String view) {
        StringBuilder request = new StringBuilder();

        request.append("SetL1");
        request.append(String.format("%02d", accNumber));
        request.append(normalDirection.getString());
        request.append(normalLedR.getString());
        request.append(normalLedG.getString());
        request.append(normalLedB.getString());
        request.append(normalSeg.getString());
        request.append(normalBuz.getString());
        request.append(answerDirection.getString());
        request.append(answerLedR.getString());
        request.append(answerLedG.getString());
        request.append(answerLedB.getString());
        request.append(answerSeg.getString());
        request.append(answerBuz.getString());
        request.append(String.format("%04d", unit));
        request.append(String.format("%5s", view));
        return checkErrorCode(sendRequest("SetL1", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     *
     * @param accNumber
     * @param normalDirection
     * @param normalLedR
     * @param normalLedG
     * @param normalLedB
     * @param normalSeg
     * @param normalBuz
     * @param answerDirection
     * @param answerLedR
     * @param answerLedG
     * @param answerLedB
     * @param answerSeg
     * @param answerBuz
     * @param units
     * @param views
     * @return
     */
    public boolean cmdSetL1(
            int accNumber,
            LinkBoxEnums normalDirection,
            LinkBoxEnums normalLedR, LinkBoxEnums normalLedG, LinkBoxEnums normalLedB,
            LinkBoxEnums normalSeg, LinkBoxEnums normalBuz,
            LinkBoxEnums answerDirection,
            LinkBoxEnums answerLedR, LinkBoxEnums answerLedG, LinkBoxEnums answerLedB,
            LinkBoxEnums answerSeg, LinkBoxEnums answerBuz,
            List<Integer> units, List<String> views) {
        StringBuilder request = new StringBuilder();

        request.append("SetL1");
        request.append(String.format("%02d", accNumber));
        request.append(normalDirection.getString());
        request.append(normalLedR.getString());
        request.append(normalLedG.getString());
        request.append(normalLedB.getString());
        request.append(normalSeg.getString());
        request.append(normalBuz.getString());
        request.append(answerDirection.getString());
        request.append(answerLedR.getString());
        request.append(answerLedG.getString());
        request.append(answerLedB.getString());
        request.append(answerSeg.getString());
        request.append(answerBuz.getString());
        for (int i = 0; i < units.size(); i++) {
            request.append(String.format("%04d", units.get(i)));
            request.append(String.format("%5s", views.get(i)));
        }
        return checkErrorCode(sendRequest("SetL1", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     *
     * @param accNumber
     * @param normalDirection
     * @param normalLed
     * @param normalSeg
     * @param normalBuz
     * @param answerDirection
     * @param answerLed
     * @param answerSeg
     * @param answerBuz
     * @param unit
     * @param view
     * @return
     */
    public boolean cmdSetL1(
            int accNumber,
            LinkBoxEnums normalDirection, LinkBoxEnums normalLed, LinkBoxEnums normalSeg, LinkBoxEnums normalBuz,
            LinkBoxEnums answerDirection, LinkBoxEnums answerLed, LinkBoxEnums answerSeg, LinkBoxEnums answerBuz,
            int unit, String view) {
        LinkBoxEnums normalLedR, normalLedG, normalLedB;
        LinkBoxEnums answerLedR, answerLedG, answerLedB;

        // Normal
        switch (normalLed) {
            case LED_OFF:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_RED:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_YELLOW:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_MAGENTA:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_WHITE:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_RED_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_YELLOW_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_MAGENTA_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_WHITE_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_RED_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_YELLOW_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_MAGENTA_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_WHITE_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_NO_CHANGE:
                normalLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                normalLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                normalLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
            default:
                normalLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                normalLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                normalLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
        }

        // Answer
        switch (answerLed) {
            case LED_OFF:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_RED:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_YELLOW:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_MAGENTA:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_WHITE:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_RED_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_YELLOW_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_MAGENTA_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_WHITE_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_RED_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_YELLOW_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_MAGENTA_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_WHITE_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_NO_CHANGE:
                answerLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                answerLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                answerLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
            default:
                answerLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                answerLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                answerLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
        }

        return cmdSetL1(
                accNumber,
                normalDirection,
                normalLedR, normalLedG, normalLedB,
                normalSeg, normalBuz,
                answerDirection,
                answerLedR, answerLedG, answerLedB,
                answerSeg, answerBuz,
                unit, view);
    }

    /**
     *
     * @param accNumber
     * @param normalDirection
     * @param normalLed
     * @param normalSeg
     * @param normalBuz
     * @param answerDirection
     * @param answerLed
     * @param answerSeg
     * @param answerBuz
     * @param units
     * @param views
     * @return
     */
    public boolean cmdSetL1(
            int accNumber,
            LinkBoxEnums normalDirection, LinkBoxEnums normalLed, LinkBoxEnums normalSeg, LinkBoxEnums normalBuz,
            LinkBoxEnums answerDirection, LinkBoxEnums answerLed, LinkBoxEnums answerSeg, LinkBoxEnums answerBuz,
            List<Integer> units, List<String> views) {
        LinkBoxEnums normalLedR, normalLedG, normalLedB;
        LinkBoxEnums answerLedR, answerLedG, answerLedB;

        // Normal
        switch (normalLed) {
            case LED_OFF:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_RED:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_YELLOW:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_MAGENTA:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_WHITE:
                normalLedR = LinkBoxEnums.LEDR_LIGHT;
                normalLedG = LinkBoxEnums.LEDG_LIGHT;
                normalLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_RED_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_YELLOW_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_MAGENTA_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_WHITE_BLINK:
                normalLedR = LinkBoxEnums.LEDR_BLINK;
                normalLedG = LinkBoxEnums.LEDG_BLINK;
                normalLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_RED_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_YELLOW_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_OFF;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_MAGENTA_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_OFF;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_WHITE_FAST_BLINK:
                normalLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                normalLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                normalLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_NO_CHANGE:
                normalLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                normalLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                normalLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
            default:
                normalLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                normalLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                normalLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
        }

        // Answer
        switch (answerLed) {
            case LED_OFF:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_RED:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_YELLOW:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_MAGENTA:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_WHITE:
                answerLedR = LinkBoxEnums.LEDR_LIGHT;
                answerLedG = LinkBoxEnums.LEDG_LIGHT;
                answerLedB = LinkBoxEnums.LEDB_LIGHT;
                break;
            case LED_RED_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_YELLOW_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_MAGENTA_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_WHITE_BLINK:
                answerLedR = LinkBoxEnums.LEDR_BLINK;
                answerLedG = LinkBoxEnums.LEDG_BLINK;
                answerLedB = LinkBoxEnums.LEDB_BLINK;
                break;
            case LED_RED_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_GREEN_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_BLUE_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_YELLOW_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_OFF;
                break;
            case LED_CYAN_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_OFF;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_MAGENTA_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_OFF;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_WHITE_FAST_BLINK:
                answerLedR = LinkBoxEnums.LEDR_FAST_BLINK;
                answerLedG = LinkBoxEnums.LEDG_FAST_BLINK;
                answerLedB = LinkBoxEnums.LEDB_FAST_BLINK;
                break;
            case LED_NO_CHANGE:
                answerLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                answerLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                answerLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
            default:
                answerLedR = LinkBoxEnums.LEDR_NO_CHANGE;
                answerLedG = LinkBoxEnums.LEDG_NO_CHANGE;
                answerLedB = LinkBoxEnums.LEDB_NO_CHANGE;
                break;
        }

        return cmdSetL1(
                accNumber,
                normalDirection,
                normalLedR, normalLedG, normalLedB,
                normalSeg, normalBuz,
                answerDirection,
                answerLedR, answerLedG, answerLedB,
                answerSeg, answerBuz,
                units, views);
    }

    /**
     * 通信状態を問い合わせる。(自動応答モード中であること) 自動応答モード下で運用中に一定周期（推奨60秒～120秒程度）で送信してください。
     * アンサーキットの作業状態を問い合わせる。(自動応答モード下では使用しない) アンサーキットの作業状態を取得する必要がある場合。
     *
     * @return
     */
    public boolean cmdGetAK() {
        return checkErrorCode(sendRequest("GetAK", "GetAK", clientTimeout_, 0, socket_, bufferedReader_, printWriter_), LinkBoxEnums.ERROR_AUTO_MODE_OPERATION.getNumber());
    }

    /**
     * Link Box III 内に機器情報を登録します。 任意。 ただし、以前のLink Box
     * （ACC10171）と混在して使用する場合には、旧Link Box
     * の仕様どおり、機器の初期セットアップ時または機器の追加、削除など構成変更時に送信してください。
     *
     * @param units
     * @return
     */
    public boolean cmdStartDev(List<Integer> units) {
        StringBuilder request = new StringBuilder();
        int count = 0;

        request.append("StartDev");
        if (units != null) {
            units.stream().forEach((unit) -> {
                request.append(String.format("%04d", unit));
            });
            count = 1000 * units.size();
        }
        return checkErrorCode(sendRequest("StartDev", request.toString(), count + clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 指定アドレスのアンサーキットに表示データを表示させます。 動作モードは直前に指定されているモードが適用されます。
     *
     * @param unit
     * @param view
     * @return
     */
    public boolean cmdDemoAK(int unit, String view) {
        StringBuilder request = new StringBuilder();

        request.append("DemoAK").append(String.format("%04d", unit)).append(String.format("%5s", view));
        return checkErrorCode(sendRequest("DemoAK", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * アドレス指定で、アンサーキットの作業指示を終了させる。 Link Box
     * IIIはこのコマンドを受信すると、指定されたアンサーキットを消灯し正常終了したものとします。
     * ACC-No.を利用した一括消灯ができます。最大128台の機器を指定可能です。
     *
     * @param unit
     * @return
     */
    public boolean cmdClearAK(int unit) {
        StringBuilder request = new StringBuilder();

        request.append("ClearAK").append(String.format("%04d", unit));
        return checkErrorCode(sendRequest("ClearAK", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     *
     * @param units
     * @return
     */
    public boolean cmdClearAK(List<Integer> units) {
        StringBuilder request = new StringBuilder();

        request.append("ClearAK");
        units.stream().forEach((unit) -> {
            request.append(String.format("%04d", unit));
        });
        return checkErrorCode(sendRequest("ClearAK", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 指定された機器をロックします。 ロックされた機器に対しては、ロック発行元以外のホストからのコマンド発行が禁止されます。(#InitAK,
     * #ChkAKは除く)
     *
     * @param unit
     * @return
     */
    public boolean cmdLock(int unit) {
        StringBuilder request = new StringBuilder();

        request.append("Lock").append(String.format("%04d", unit));
        return checkErrorCode(sendRequest("Lock", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 指定された機器のロックを解除します。ロックを発行したホストのみ有効です。
     *
     * @param unit
     * @return
     */
    public boolean cmdUnLock(int unit) {
        StringBuilder request = new StringBuilder();

        request.append("UnLock").append(String.format("%04d", unit));
        return checkErrorCode(sendRequest("UnLock", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 指定された機器のロック状態を取得します。どのホストからでも有効です。
     *
     * @param unit
     * @return
     */
    public boolean cmdGetLock(int unit) {
        StringBuilder request = new StringBuilder();

        request.append("GetLock").append(String.format("%04d", unit));
        return checkErrorCode(sendRequest("GetLock", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 指定された機器のロックを無条件で解除します。どのホストからでも有効です。
     *
     * @param unit
     * @return
     */
    public boolean cmdClearLock(int unit) {
        StringBuilder request = new StringBuilder();

        request.append("ClearLock").append(String.format("%04d", unit));
        return checkErrorCode(sendRequest("ClearLock", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * 指定された機器のロックを無条件で解除します。どのホストからでも有効です。
     *
     * @return
     */
    public boolean cmdGetErrCode() {
        return checkErrorCode(sendRequest("GetErrCode", "GetErrCode", clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * Link Box IIIが基板上及び外部セグメントに表示しているエラーコードを初期表示に戻します。(エラー発生履歴は全て消去されます。)
     *
     * @return
     */
    public boolean cmdClearErrCode() {
        return checkErrorCode(sendRequest("ClearErrCode", "ClearErrCode", clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
    }

    /**
     * Link Box III にIP アドレス及びサブネットマスク、デフォルトゲートウェイを設定する。
     *
     * @param address
     * @param mask
     * @param gateway
     * @return
     */
    public boolean cmdSetIPAddr(String address, String mask, String gateway) {
        try {
            StringBuilder request = new StringBuilder();
            byte adr[] = InetAddress.getByName(address).getAddress();
            byte msk[] = InetAddress.getByName(mask).getAddress();
            byte gwy[] = InetAddress.getByName(gateway).getAddress();

            request.append("SetIPAddr");
            request.append("<").append(String.format("%03d%03d%03d%03d", Byte.toUnsignedInt(adr[0]), Byte.toUnsignedInt(adr[1]), Byte.toUnsignedInt(adr[2]), Byte.toUnsignedInt(adr[3]))).append(">");
            request.append("<").append(String.format("%03d%03d%03d%03d", Byte.toUnsignedInt(msk[0]), Byte.toUnsignedInt(msk[1]), Byte.toUnsignedInt(msk[2]), Byte.toUnsignedInt(msk[3]))).append(">");
            request.append("<").append(String.format("%03d%03d%03d%03d", Byte.toUnsignedInt(gwy[0]), Byte.toUnsignedInt(gwy[1]), Byte.toUnsignedInt(gwy[2]), Byte.toUnsignedInt(gwy[3]))).append(">");

            return checkErrorCode(sendRequest("SetIPAddr", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
        } catch (UnknownHostException ex) {
            exceptionCaught(ex);
        }
        return false;
    }

    /**
     * Link Box III にホストPC のIP アドレス（以下、ホストIP アドレス）を登録する。
     *
     * @param address
     * @return
     */
    public boolean cmdSetHostAddr(List<String> address) {
        try {
            StringBuilder request = new StringBuilder();

            request.append("SetHostAddr");
            for (int i = 0; i < address.size(); i++) {
                byte adr[] = InetAddress.getByName(address.get(i)).getAddress();

                request.append("<").append(String.format("%03d%03d%03d%03d", Byte.toUnsignedInt(adr[0]), Byte.toUnsignedInt(adr[1]), Byte.toUnsignedInt(adr[2]), Byte.toUnsignedInt(adr[3]))).append(">");
            }
            return checkErrorCode(sendRequest("SetHostAddr", request.toString(), clientTimeout_, 0, socket_, bufferedReader_, printWriter_));
        } catch (UnknownHostException ex) {
            exceptionCaught(ex);
        }
        return false;
    }

    /**
     * Link Box III をリモートで再起動する。
     *
     * @return
     */
    public boolean cmdReboot() {
        if (checkErrorCode(sendRequest("Reboot", "Reboot", clientTimeout_, 0, socket_, bufferedReader_, printWriter_))) {
            return checkErrorCode(sendRequest("Reboot", "RebootR5F70855", clientTimeout_, 5000, socket_, bufferedReader_, printWriter_));
        }
        return false;
    }

    /**
     *
     * @return
     */
    public boolean start() {
        if (serverAddress_ != null) {
            if (cmdConnect()) {
                try {
                    inetSocketAddress_ = new InetSocketAddress(serverAddress_, clientPort_);
                    if (socket_ != null) {
                        socket_.close();
                    }
                    socket_ = new Socket();
                    socket_.connect(inetSocketAddress_, 30000);

                    // Wait for eco mode
                    try {
                        TimeUnit.MILLISECONDS.sleep(3000);
                    } catch (InterruptedException ex) {
                        exceptionCaught(ex);
                    }

                    bufferedReader_ = new BufferedReader(new InputStreamReader(socket_.getInputStream(), "UTF-8"));
                    printWriter_ = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket_.getOutputStream(), "UTF-8")));

                    isRunning_ = true;
                    for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
                        listener.startLinkBoxClient();
                    }
                    return true;
                } catch (IOException ex) {
                    exceptionCaught(ex);
                }
            } else {
                for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
                    listener.errorLinkBoxClient(LinkBoxEnums.ERROR_WORK_ORDER_PORT_ACQUISITION.getNumber(), CapAiSystemEnums.FAILED_WORK_ORDER_PORT_ACQUISITION.toString());
                }
            }
        } else {
            for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
                listener.errorLinkBoxClient(LinkBoxEnums.ERROR_SET_ADDRESS.getNumber(), CapAiSystemEnums.SET_ADDRESS.toString());
            }
        }
        isRunning_ = false;
        return false;
    }

    /**
     *
     */
    public void stop() {
        if (isRunning_) {
            cmdDisconnect();
        }

        if (bufferedReader_ != null) {
            try {
                bufferedReader_.close();
                bufferedReader_ = null;
            } catch (IOException ex) {
                exceptionCaught(ex);
            }
        }

        if (printWriter_ != null) {
            printWriter_.close();
            printWriter_ = null;
        }

        if (socket_ != null) {
            try {
                socket_.close();
                socket_ = null;
            } catch (IOException ex) {
                exceptionCaught(ex);
            }
        }

        isRunning_ = false;
        for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
            listener.stopLinkBoxClient();
        }
    }

    /**
     *
     * @param cause
     */
    public void exceptionCaught(Throwable cause) {
        for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
            listener.errorLinkBoxClient(LinkBoxEnums.ERROR_EXCEPTION.getNumber(), cause.getMessage());
        }
    }

    synchronized private int sendRequest(String command, String request, int timeout, int wait, Socket sock, BufferedReader br, PrintWriter pw) {
        StringBuilder buffer = new StringBuilder();
        long currentTime;
        int data = 0;
        int result = LinkBoxEnums.ERROR_REQUEST.getNumber();

        for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
            listener.requestLinkBoxClient(command, request);
        }

        try {
            sock.setSoTimeout(timeout);

            if (pw != null) {
                pw.write(request + "\r");
                pw.flush();
            }

            currentTime = System.currentTimeMillis() + timeout;
            while (data != 0x0d) {
                while (br.ready()) {
                    data = br.read();

                    switch (data) {
                        case -1:
                        case 0x0d:
                            break;
                        default:
                            if (timeout > 0) {
                                currentTime = System.currentTimeMillis() + timeout;
                            }
                            buffer.append(new String(new byte[]{(byte) data}, "US-ASCII"));
                            break;
                    }
                }

                if ((currentTime > 0) && (currentTime < System.currentTimeMillis())) {
                    currentTime = 0;
                    data = 0x0d;
                    buffer.delete(0, buffer.length());
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException ex) {
                    exceptionCaught(ex);
                }
            }

            if (buffer.length() == 0) {
                result = LinkBoxEnums.ERROR_NO_RESPONSE.getNumber();
            } else {
                result = checkResponse(command, buffer.toString());
            }

            if (wait > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(wait);
                } catch (InterruptedException ex) {
                    exceptionCaught(ex);
                }
            }
        } catch (IOException | NullPointerException ex) {
            exceptionCaught(ex);
        }
        return result;
    }

    synchronized private int checkResponse(String command, String response) {
        int errorCode;
        String ipAddress = null;
        Integer unitNumber = null;
        Integer portNumber = null;
        Integer ipAddress1 = null;
        String status = null;

        if (response.startsWith("PortClose")) {
            switch (response.length()) {
                case 11:
                    errorCode = Integer.parseInt(response.substring(9, 11), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("GetPortInfo")) {
            switch (response.length()) {
                case 13:
                    errorCode = Integer.parseInt(response.substring(11, 13), 16);
                    break;
                case 21:
                    errorCode = Integer.parseInt(response.substring(11, 13), 16);
                    ipAddress = response.substring(13, 21);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("Connect")) {
            switch (response.length()) {
                case 9:
                    errorCode = Integer.parseInt(response.substring(7, 9), 16);
                    break;
                case 13:
                    errorCode = Integer.parseInt(response.substring(7, 9), 16);
                    clientPort_ = Integer.parseInt(response.substring(9, 13), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("Disconnect")) {
            switch (response.length()) {
                case 12:
                    errorCode = Integer.parseInt(response.substring(10, 12), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("InitAK")) {
            switch (response.length()) {
                case 8:
                    errorCode = Integer.parseInt(response.substring(6, 8), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("Mnt")) {
            switch (response.length()) {
                case 5:
                    errorCode = Integer.parseInt(response.substring(3, 5), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("AddrAK")) {
            switch (response.length()) {
                case 8:
                    errorCode = Integer.parseInt(response.substring(6, 8), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("SetAutoATT")) {
            switch (response.length()) {
                case 12:
                    errorCode = Integer.parseInt(response.substring(10, 12), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("ClearAutoATT")) {
            switch (response.length()) {
                case 14:
                    errorCode = Integer.parseInt(response.substring(12, 14), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("SetL1")) {
            switch (response.length()) {
                case 7:
                    errorCode = Integer.parseInt(response.substring(5, 7), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("SetL1")) {
            switch (response.length()) {
                case 7:
                    errorCode = Integer.parseInt(response.substring(5, 7), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("GetAK")) {
            switch (response.length()) {
                case 8:
                    errorCode = Integer.parseInt(response.substring(5, 7), 16);
                    status = response.substring(7, 8);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
            // ※追加予定(自動応答モード以外)
        } else if (response.startsWith("StartDev")) {
            switch (response.length()) {
                case 10:
                    errorCode = Integer.parseInt(response.substring(8, 10), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("DemoAK")) {
            switch (response.length()) {
                case 8:
                    errorCode = Integer.parseInt(response.substring(6, 8), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("ClearAK")) {
            switch (response.length()) {
                case 9:
                    errorCode = Integer.parseInt(response.substring(7, 9), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("ClearAK")) {
            switch (response.length()) {
                case 9:
                    errorCode = Integer.parseInt(response.substring(7, 9), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("Lock")) {
            switch (response.length()) {
                case 6:
                    errorCode = Integer.parseInt(response.substring(4, 6), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("UnLock")) {
            switch (response.length()) {
                case 8:
                    errorCode = Integer.parseInt(response.substring(6, 8), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("GetLock")) {
            switch (response.length()) {
                case 9:
                    errorCode = Integer.parseInt(response.substring(7, 9), 16);
                    break;
                case 25:
                    errorCode = Integer.parseInt(response.substring(7, 9), 16);
                    unitNumber = Integer.parseInt(response.substring(9, 13), 10);
                    ipAddress = response.substring(13, 21);
                    portNumber = Integer.parseInt(response.substring(21, 25), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("ClearLock")) {
            switch (response.length()) {
                case 11:
                    errorCode = Integer.parseInt(response.substring(9, 11), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("GetErrCode")) {
            switch (response.length()) {
                case 13:
                    ipAddress1 = Integer.parseInt(response.substring(10, 11), 10);
                    errorCode = Integer.parseInt(response.substring(11, 13), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("ClearErrCode")) {
            switch (response.length()) {
                case 14:
                    errorCode = Integer.parseInt(response.substring(12, 14), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("SetIPAddr")) {
            switch (response.length()) {
                case 11:
                    errorCode = Integer.parseInt(response.substring(9, 11), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("SetHostAddr")) {
            switch (response.length()) {
                case 13:
                    errorCode = Integer.parseInt(response.substring(11, 13), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else if (response.startsWith("Reboot")) {
            switch (response.length()) {
                case 8:
                    errorCode = Integer.parseInt(response.substring(6, 8), 16);
                    break;
                case 15:
                    errorCode = Integer.parseInt(response.substring(13, 15), 16);
                    break;
                default:
                    errorCode = LinkBoxEnums.ERROR_REQUEST_DATA.getNumber();
                    break;
            }
        } else {
            errorCode = LinkBoxEnums.ERROR_REQUEST.getNumber();
        }

        for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
            listener.responseLinkBoxClient(command, response, errorCode, ipAddress, unitNumber, portNumber, ipAddress1, status);
        }
        return errorCode;
    }

    private boolean checkErrorCode(int errorCode) {
        return checkErrorCode(errorCode, LinkBoxEnums.ERROR_NONE.getNumber());
    }

    private boolean checkErrorCode(int errorCode, int nonErrorCode) {
        if (errorCode == nonErrorCode) {
            return true;
        }

        String message;

        if (errorCode == LinkBoxEnums.ERROR_DEVICE_REGISTER.getNumber()) {
            message = CapAiSystemEnums.DEVICE_REGISTRATION_EXECUTION.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_ZERO_ADDRESS.getNumber()) {
            message = CapAiSystemEnums.NO_0_ADDRESSING.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_WILDCARD_ADDRESS.getNumber()) {
            message = CapAiSystemEnums.WILD_CARD_ADDRESSING.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DEVICE_LOCKED.getNumber()) {
            message = CapAiSystemEnums.SPECIFY_LOCKED_EQUIPMENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DEVICE_PARTLY_LOCKED.getNumber()) {
            message = CapAiSystemEnums.SPECIFY_PART_LOCKED_EQUIPMENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_NOT_CONNECTED.getNumber()) {
            message = CapAiSystemEnums.DISCONNECTED.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DUPLICATE_CONNECTION.getNumber()) {
            message = CapAiSystemEnums.OVERLAPPING_CONNECTION.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_CONNECTIONS_OVER.getNumber()) {
            message = CapAiSystemEnums.OVER_CONNECTIONS.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DEVICE_ADDRESS.getNumber()) {
            message = CapAiSystemEnums.ABNORMAL_EQUIPMENT_ADDRESS.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DEVICE_MAINTENANCE.getNumber()) {
            message = CapAiSystemEnums.SPECIFY_DURING_MAINTENANCE_EQUIPMENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DEVICE_WORKING.getNumber()) {
            message = CapAiSystemEnums.SPECIFY_WORK_ORDERS_EQUIPMENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DEVICE_OPENING.getNumber()) {
            message = CapAiSystemEnums.SPECIFY_OPEN_EQUIPMENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_POLLING_ADDRESS.getNumber()) {
            message = CapAiSystemEnums.ABNORMAL_POLLING_RESPONSE_ADDRESS.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_BARCODE_READER.getNumber()) {
            message = CapAiSystemEnums.ABNORMAL_BARCODE_READER_INTERFACE.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_STATUS.getNumber()) {
            message = CapAiSystemEnums.STATUS_ERROR.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_ADDRESSING.getNumber()) {
            message = CapAiSystemEnums.ABNORMAL_ADDRESSING.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_TIMEOUT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_RECEIVE_TIMEOUT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_INTERRUPT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_ERROR_INTERRUPT_OCCURS.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_BCC.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_ABNORMAL_BCC.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_FORMAT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_ABNORMAL_RECEPTION_DENBUN_FORMAT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_STATUS.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_ANN_CIRCUIT_STATUS_ERROR.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_NO_FREE_PORT.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_NO_FREE_CONNECTION_PORT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_INVALID_COMMAND.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_DISABLE_COMMAND.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_COMMAND_PARAMETER.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_COMMAND_PARAMETER_ERROR.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_INVALID_IP.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_INVALID_HOST_IP.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_NOT_HOST_COMMAND.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMAND_NOT_EXECUTABLE.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_NOT_AKATT.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_AKATT_NOT_ACCEPTED.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_SOCKET.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_SOCKET_CONNECTION_ERROR.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_CH1_OVERCURRENT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_CH1_OVERCURRENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_CH2_OVERCURRENT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_CH2_OVERCURRENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_CH3_OVERCURRENT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_CH3_OVERCURRENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_CH4_OVERCURRENT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_CH4_OVERCURRENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_CH5_OVERCURRENT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_CH5_OVERCURRENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_LOCAL_CH6_OVERCURRENT.getNumber()) {
            message = CapAiSystemEnums.LOCAL_COMMUNICATION_CH6_OVERCURRENT.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_AUTO_MODE_OPERATION.getNumber()) {
            message = CapAiSystemEnums.OPERATION_AUTOMATIC_COMMUNICATION_MODE.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DUPLICATE_CONTROL1.getNumber()) {
            message = CapAiSystemEnums.DUPLICATION_CONTROL_1.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_DUPLICATE_CONTROL2.getNumber()) {
            message = CapAiSystemEnums.DUPLICATION_CONTROL_2.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_CONTROL_DISABLED1.getNumber()) {
            message = CapAiSystemEnums.INVALID_CONTROL_1.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_CONTROL_DISABLED2.getNumber()) {
            message = CapAiSystemEnums.INVALID_CONTROL_2.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_HOST_DISCONNECTION.getNumber()) {
            message = CapAiSystemEnums.HOST_COMMUNICATION_FORCED_DISCONNECTION.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_REQUEST.getNumber()) {
            message = CapAiSystemEnums.ABNORMAL_REQUEST_RECEIVING.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_REQUEST_DATA.getNumber()) {
            message = CapAiSystemEnums.REQUEST_SENDING_RECEIVING_DATA_ERROR.toString();
        } else if (errorCode == LinkBoxEnums.ERROR_NO_RESPONSE.getNumber()) {
            message = CapAiSystemEnums.NO_RESPONSE.toString();
        } else {
            message = "???";
        }
        for (LinkBoxClientListener listener : eventListenerList_.getListeners(LinkBoxClientListener.class)) {
            listener.errorLinkBoxClient(errorCode, message);
        }
        return false;
    }
}
