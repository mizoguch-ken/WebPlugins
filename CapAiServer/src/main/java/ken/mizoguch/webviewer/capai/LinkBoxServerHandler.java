package ken.mizoguch.webviewer.capai;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import ken.mizoguch.webviewer.plugin.kit.PluginI18n;

/**
 *
 * @author mizoguch-ken
 */
public final class LinkBoxServerHandler extends ChannelInboundHandlerAdapter {

    private static final int MAX_BUFFER_LENGTH = 4096;
    private static final PluginI18n.Bundle I18N = PluginI18n.bundle(LinkBoxServerHandler.class);

    private final LinkBoxServer linkBoxServer_;
    private final StringBuilder buffer_ = new StringBuilder();

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

        try {
            while (in.isReadable()) {
                byte data = in.readByte();
                if (data == 0x0d) {
                    String response = linkBoxServer_.createResponse(buffer_.toString());
                    if (response != null) {
                        ByteBuf out = ctx.alloc().buffer(response.length());
                        out.writeBytes(response.getBytes(StandardCharsets.US_ASCII));
                        ctx.writeAndFlush(out);
                    }
                    buffer_.setLength(0);
                } else if (data != 0x0a) {
                    if (buffer_.length() >= MAX_BUFFER_LENGTH) {
                        buffer_.setLength(0);
                        linkBoxServer_.exceptionCaught(
                                new IllegalStateException(I18N.get("capai.server.frameTooLarge")));
                        ctx.close();
                        return;
                    }
                    buffer_.append((char) (data & 0xFF));
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
