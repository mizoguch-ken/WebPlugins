/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.capai;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author mizoguch-ken
 */
public final class LinkBoxServerHandler extends ChannelHandlerAdapter {

    private final LinkBoxServer linkBoxServer_;

    /**
     *
     * @param linkBoxServer
     */
    public LinkBoxServerHandler(LinkBoxServer linkBoxServer) {
        linkBoxServer_ = linkBoxServer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        ByteBuf out = ctx.alloc().buffer(2);
        StringBuilder buffer = new StringBuilder();
        String buf;
        byte data;

        try {
            while (in.isReadable()) {
                data = in.readByte();
                switch (data) {
                    case 0x0a:
                        break;
                    case 0x0d:
                        buf = linkBoxServer_.createResponse(buffer.toString());
                        if (buf != null) {
                            out.writeBytes(buf.getBytes("US-ASCII"));
                            ctx.channel().writeAndFlush(out);
                            out.clear();
                        }
                        buffer.delete(0, buffer.length());
                        break;
                    default:
                        buffer.append(new String(new byte[]{data}, "US-ASCII"));
                        break;
                }
            }
        } catch (UnsupportedEncodingException ex) {
            linkBoxServer_.exceptionCaught(ex);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        linkBoxServer_.exceptionCaught(cause);
        ctx.close();
    }
}
