package dev.starbloom.orchestra.music;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.slf4j.Logger;

/**
 * 发往客户端的包的处理器登记处。1.21.1 的 NeoForge 没有 {@code RegisterClientPayloadHandlersEvent}，
 * {@code playToClient} 注册时就要给处理器；可公共代码不能引用客户端的类，所以注册时只放一个转发
 * （{@link #dispatch}），客户端代码（MusicClient、Conducting）在客户端初始化时用 {@link #register} 填上真正的处理器。
 * 处理器在收到包时才查，所以只要在进服之前登记好就行；专用服务端上什么都不登记，也收不到这些包。
 */
public final class ClientPayloadHandlers {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<CustomPacketPayload.Type<?>, IPayloadHandler<?>> HANDLERS = new ConcurrentHashMap<>();
    private static final Set<CustomPacketPayload.Type<?>> WARNED = ConcurrentHashMap.newKeySet();

    private ClientPayloadHandlers() {}

    /** 客户端登记某种包的处理器（同一种包再登记一次会换掉前一个）。 */
    public static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
        HANDLERS.put(type, handler);
    }

    /** 注册包时交给 {@code playToClient} 的处理器：收到包时转给客户端登记的那个。 */
    public static <T extends CustomPacketPayload> IPayloadHandler<T> dispatch(CustomPacketPayload.Type<T> type) {
        return (payload, context) -> handle(type, payload, context);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void handle(CustomPacketPayload.Type<T> type, T payload, IPayloadContext context) {
        IPayloadHandler<T> handler = (IPayloadHandler<T>) HANDLERS.get(type);
        if (handler != null) {
            handler.handle(payload, context);
        } else if (WARNED.add(type)) {
            LOG.warn("No client handler registered for payload {}", type.id());
        }
    }
}
