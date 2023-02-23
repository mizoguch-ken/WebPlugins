/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javax.swing.event.EventListenerList;

/**
 *
 * @author mizoguch-ken
 */
public final class LinkBoxServer extends Service<Void> {

    private final LinkBoxServer self_;
    private final EventListenerList eventListenerList_;

    private final EventLoopGroup bossGroup_;
    private final EventLoopGroup workerGroup_;

    private int serverPort_;

    /**
     *
     */
    public LinkBoxServer() {
        self_ = this;
        eventListenerList_ = new EventListenerList();
        bossGroup_ = new NioEventLoopGroup();
        workerGroup_ = new NioEventLoopGroup();
        setListenPort(50021);
    }

    /**
     *
     * @param listener
     */
    public void addLinkBoxServerListener(LinkBoxServerListener listener) {
        boolean isListener = false;
        for (LinkBoxServerListener lbsl : eventListenerList_.getListeners(LinkBoxServerListener.class)) {
            if (lbsl == listener) {
                isListener = true;
                break;
            }
        }
        if (!isListener) {
            eventListenerList_.add(LinkBoxServerListener.class, listener);
        }
    }

    /**
     *
     * @param listener
     */
    public void removeLinkBoxServerListener(LinkBoxServerListener listener) {
        eventListenerList_.remove(LinkBoxServerListener.class, listener);
    }

    /**
     *
     * @param port
     */
    public void setListenPort(int port) {
        serverPort_ = port;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() {
                for (LinkBoxServerListener listener : eventListenerList_.getListeners(LinkBoxServerListener.class)) {
                    listener.startLinkBoxServer();
                }
                if (serverPort_ > 50000) {
                    try {
                        ServerBootstrap b = new ServerBootstrap();
                        b.group(bossGroup_, workerGroup_)
                                .channel(NioServerSocketChannel.class)
                                .childHandler(new ChannelInitializer<SocketChannel>() {
                                    @Override
                                    public void initChannel(SocketChannel ch) throws Exception {
                                        ch.pipeline().addLast(new LinkBoxServerHandler(self_));
                                    }
                                });
                        ChannelFuture f = b.bind(serverPort_).sync();
                        f.channel().closeFuture().sync();
                    } catch (InterruptedException ex) {
                        exceptionCaught(ex);
                    } finally {
                        stop();
                    }
                }
                for (LinkBoxServerListener listener : eventListenerList_.getListeners(LinkBoxServerListener.class)) {
                    listener.stopLinkBoxServer();
                }
                return null;
            }
        };
    }

    /**
     *
     */
    public void stop() {
        if (isRunning()) {
            cancel();
        }
        workerGroup_.shutdownGracefully();
        bossGroup_.shutdownGracefully();
    }

    /**
     *
     * @param cause
     */
    public void exceptionCaught(Throwable cause) {
        for (LinkBoxServerListener listener : eventListenerList_.getListeners(LinkBoxServerListener.class)) {
            listener.errorLinkBoxServer(LinkBoxEnums.ERROR_EXCEPTION.getNumber(), cause.getMessage());
        }
    }

    /**
     *
     * @param request
     * @return
     */
    synchronized public String createResponse(String request) {
        StringBuilder buf = new StringBuilder();
        Integer errorCode = null;
        Integer unitNumber = null;
        Integer status = null;
        String view = null;

        if (request.startsWith("AKATT")) {
            switch (request.length()) {
                case 13:
                    buf.append("O");
                    errorCode = Integer.parseInt(request.substring(5, 7), 16);
                    unitNumber = Integer.parseInt(request.substring(7, 11), 10);
                    status = Integer.parseInt(request.substring(11, 13), 16);
                    break;
                case 20:
                    buf.append("O");
                    errorCode = Integer.parseInt(request.substring(5, 7), 16);
                    unitNumber = Integer.parseInt(request.substring(7, 11), 10);
                    status = Integer.parseInt(request.substring(11, 13), 16);
                    view = request.substring(14, 19);
                    break;
                default:
                    buf.append("N");
                    break;
            }
            for (LinkBoxServerListener listener : eventListenerList_.getListeners(LinkBoxServerListener.class)) {
                listener.requestLinkBoxServer("AKATT", request, errorCode, unitNumber, status, view);
                listener.responseLinkBoxServer("AKATT", buf.toString());
            }
            buf.append("\n").toString();
            return buf.toString();
        }
        return null;
    }
}
