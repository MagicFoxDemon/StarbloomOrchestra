package dev.starbloom.orchestra.music;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

/**
 * 发往客户端的包的处理器登记处。公共代码注册网络包时不能引用客户端的类，所以注册时只放一个转发
 * （{@link #dispatch}），客户端代码（MusicClient、Conducting）在客户端初始化时用 {@link #register} 填上真正的处理器。
 * 处理器在收到包时才查，所以只要在进服之前登记好就行；专用服务端上什么都不登记，也收不到这些包。
 */
public final class ClientPayloadHandlers {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<Class<?>, Consumer<?>> HANDLERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> WARNED = ConcurrentHashMap.newKeySet();

    private ClientPayloadHandlers() {}

    /** 客户端登记某种包的处理器（同一种包再登记一次会换掉前一个）。 */
    public static <T> void register(Class<T> type, Consumer<T> handler) {
        HANDLERS.put(type, handler);
    }

    /** 收到发往客户端的包时交给客户端登记的那个处理器。 */
    @SuppressWarnings("unchecked")
    public static <T> void dispatch(T msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            context.enqueueWork(() -> {
                Consumer<T> handler = (Consumer<T>) HANDLERS.get(msg.getClass());
                if (handler != null) {
                    handler.accept(msg);
                } else if (WARNED.add(msg.getClass())) {
                    LOG.warn("No client handler registered for payload {}", msg.getClass().getName());
                }
            });
        }
        context.setPacketHandled(true);
    }
}
