package ken.mizoguch.webviewer.capai;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author mizoguch-ken
 */
public final class LinkBoxServerHandler extends ChannelInboundHandlerAdapter {

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
        if (!(msg instanceof ByteBuf)) {
            ReferenceCountUtil.release(msg);
            return;
        }

        ByteBuf in = (ByteBuf) msg;
        StringBuilder buffer = new StringBuilder();

        try {
            while (in.isReadable()) {
                byte data = in.readByte();
                if (data == 0x0d) {
                    String response = linkBoxServer_.createResponse(buffer.toString());
                    if (response != null) {
                        ByteBuf out = ctx.alloc().buffer(response.length());
                        out.writeBytes(response.getBytes(StandardCharsets.US_ASCII));
                        ctx.writeAndFlush(out);
                    }
                    buffer.setLength(0);
                } else if (data != 0x0a) {
                    buffer.append((char) data);
                }
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        linkBoxServer_.exceptionCaught(cause);
        ctx.close();
    }
}
