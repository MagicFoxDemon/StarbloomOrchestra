package dev.starbloom.orchestra;

import com.google.gson.*;
import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 物品拿在手上/掉在地上不能"比正常大一圈"，物品模型要是 1.20.1 读得了的格式。
 *
 * <p>实机反馈：浅色精灵木板与叶辉嵌片的手持模型和凋落物太大。原因是它们的方块模型没有
 * `parent`（也没写 `display`），物品形态拿不到原版的 display 变换，于是整块 16×16 直接
 * 糊在手上和地上。
 *
 * <p>1.20.1 没有物品模型定义（`items/*.json` 是 1.21.4 起的），物品 `<id>` 直接用 `models/item/<id>.json`；
 * `models/item/` 里另有只供渲染、不是物品的模型（{@link #renderOnly}）。
 */
class ItemModelDisplayTest {
    private static final String ROOT="assets/starbloom_orchestra/";
    private static final String NS="starbloom_orchestra:";
    /** 元素转动超出 1.20.1 原版能读的范围时用的几何加载器（client/music/RotatedCuboidModel）。 */
    private static final String ROTATED_LOADER=NS+"rotated_cuboids";
    private static final Set<Double> VANILLA_ANGLES=Set.of(0.0,22.5,-22.5,45.0,-45.0);

    private static JsonObject read(String path)throws IOException{
        try(var input=ItemModelDisplayTest.class.getResourceAsStream("/"+ROOT+path)){
            assertNotNull(input,"缺少资源："+path);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static boolean exists(String path){
        return ItemModelDisplayTest.class.getResource("/"+ROOT+path)!=null;
    }

    /**
     * 只供渲染、不是物品的模型：拿在手上演奏的乐器和工位上的琴槌（`*_held`）、演奏中换上的（`*_playing`）、小提琴的弓。
     * 客户端按模型 id 单独加载它们（HandheldPoses、StationPoses）。
     */
    static boolean renderOnly(String name){
        return name.endsWith("_held")||name.endsWith("_playing")||name.equals("hexen_violin_bow");
    }

    /**
     * sub（如 "models/item"、"models/block"）下的 JSON 名字（不含子目录）；资源可能是目录也可能打进了 jar。
     */
    static List<String> names(String sub)throws Exception{
        var ids=new ArrayList<String>();
        var url=ItemModelDisplayTest.class.getResource("/"+ROOT+"lang/zh_cn.json");
        assertNotNull(url,"测试类路径里没有语言文件");
        if("file".equals(url.getProtocol())||"union".equals(url.getProtocol())){
            var dir=Path.of(url.toURI()).getParent().getParent().resolve(sub);
            if(!Files.isDirectory(dir))return ids;
            try(Stream<Path> files=Files.list(dir)){
                files.filter(p->p.toString().endsWith(".json"))
                     .forEach(p->ids.add(p.getFileName().toString().replaceFirst("\\.json$","")));
            }
        }else{
            var connection=url.openConnection();
            assertTrue(connection instanceof JarURLConnection,"无法识别的资源协议："+url);
            try(var jar=((JarURLConnection)connection).getJarFile()){
                var entries=jar.entries();
                String prefix=ROOT+sub+"/";
                while(entries.hasMoreElements()){
                    String name=entries.nextElement().getName();
                    if(name.startsWith(prefix)&&name.endsWith(".json")
                        &&!name.substring(prefix.length()).contains("/"))
                        ids.add(name.substring(prefix.length()).replaceFirst("\\.json$",""));
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    /** 物品 ID：models/item/ 里除了只供渲染的模型。 */
    static List<String> itemIds()throws Exception{
        return names("models/item").stream().filter(n->!renderOnly(n)).toList();
    }

    /** "starbloom_orchestra:block/x" / "minecraft:item/y" / "block/z" → 带命名空间的引用。 */
    private static String full(String ref){
        return ref.contains(":")?ref:"minecraft:"+ref;
    }

    /** 1.20.1 的物品模型是 models/item/<id>.json：名字在语言文件里的物品（不含 tooltip 之类的子键）都要有一份。 */
    @Test void everyNamedItemHasAModel()throws Exception{
        var lang=read("lang/zh_cn.json");
        var missing=new ArrayList<String>();
        for(String key:lang.keySet()){
            if(!key.startsWith("item."+NS.replace(":","."))) continue;
            String id=key.substring(("item."+NS.replace(":",".")).length());
            if(id.contains("."))continue;
            if(!exists("models/item/"+id+".json"))missing.add(id);
        }
        assertTrue(missing.isEmpty(),"这些物品没有 models/item/<id>.json，游戏里是紫黑方块：\n"+String.join("\n",missing));
        assertFalse(exists("items/hexen_violin.json"),"1.20.1 不读 items/ 下的物品模型定义，不该再带着");
    }

    /**
     * 物品模型经过 `item/` 再 parent 到方块模型时：沿 parent 链往上找，链上得有一个模型带 `display`，
     * 或者最后落到原版模型（原版的 item/generated、block/block 自带显示变换）。
     *
     * <p>实机反馈（2026-11-14）：管风琴音管、风箱、钢片琴、钟琴、乐谱架拿在手上和掉在地上是整块大小，物品栏里是正面平视图——
     * 它们的物品模型只有 `parent: block/...`，那个方块模型没有 display。
     */
    @Test void itemModelChainsEndInADisplay()throws Exception{
        var offenders=new ArrayList<String>();
        for(String id:itemIds()){
            String ref=NS+"item/"+id;
            var chain=new ArrayList<String>();
            boolean ok=false;
            while(ref!=null&&chain.size()<16){
                chain.add(ref);
                if(!ref.startsWith(NS)){ok=true;break;}
                var model=read("models/"+ref.substring(NS.length())+".json");
                if(model.has("display")){ok=true;break;}
                ref=model.has("parent")?full(model.get("parent").getAsString()):null;
            }
            if(!ok)offenders.add(id+": "+String.join(" -> ",chain));
        }
        assertTrue(offenders.isEmpty(),
            "这些物品的模型链上没有 display，也没落到原版模型，手持、掉落、物品栏都会按原尺寸画：\n"+String.join("\n",offenders));
    }

    /**
     * 演奏时换模型：有 `<id>_playing` 的物品，模型里要有 `starbloom_orchestra:playing` 的 override 指向它
     * （26.1 的 items/<id>.json 里 minecraft:using_item 条件的替代，属性在 ItemModelProperties）。
     */
    @Test void handheldItemsSwitchToThePlayingModel()throws Exception{
        var all=new HashSet<>(names("models/item"));
        int found=0;
        for(String id:itemIds()){
            if(!all.contains(id+"_playing"))continue;
            found++;
            var model=read("models/item/"+id+".json");
            assertTrue(model.has("overrides"),id+" 没有 overrides，演奏时不会换成 _playing 模型");
            boolean ok=false;
            for(var o:model.getAsJsonArray("overrides")){
                var override=o.getAsJsonObject();
                var predicate=override.getAsJsonObject("predicate");
                if(predicate.has(NS+"playing")&&predicate.get(NS+"playing").getAsFloat()==1.0F
                    &&(NS+"item/"+id+"_playing").equals(override.get("model").getAsString()))ok=true;
            }
            assertTrue(ok,id+" 的 overrides 没有在 "+NS+"playing = 1 时换成 item/"+id+"_playing");
        }
        assertTrue(found>=9,"手持乐器（小提琴、长笛、小号、中提琴、双簧管、巴松、篠笛、神乐铃、尺八）都有演奏模型："+found);
    }

    /**
     * 1.20.1 原版的元素 rotation 只认 `axis` + `angle`，angle 只能是 0、±22.5、±45；欧拉角 `x`/`y`/`z` 和别的角度整份模型会读不出来。
     * 这类模型得交给 `starbloom_orchestra:rotated_cuboids` 加载器，元素放在 `cuboids` 里（tools/instrument_kit.py）。
     */
    @Test void elementRotationsParseIn1201()throws Exception{
        var offenders=new ArrayList<String>();
        int loaders=0;
        for(String sub:new String[]{"models/block","models/item"}){
            for(String name:names(sub)){
                String path=sub+"/"+name+".json";
                var model=read(path);
                if(model.has("loader")){
                    String loader=model.get("loader").isJsonObject()
                        ?model.getAsJsonObject("loader").get("id").getAsString():model.get("loader").getAsString();
                    if(!loader.equals(ROTATED_LOADER))continue;
                    loaders++;
                    if(model.has("elements"))offenders.add(path+": 有 loader 时元素要放在 cuboids 里，elements 会被原版先解析");
                    if(!model.has("cuboids")||model.getAsJsonArray("cuboids").isEmpty())offenders.add(path+": 没有 cuboids");
                    continue;
                }
                if(model.has("cuboids"))offenders.add(path+": 有 cuboids 却没写 loader，元素不会画出来");
                if(!model.has("elements"))continue;
                int i=0;
                for(var e:model.getAsJsonArray("elements")){
                    var element=e.getAsJsonObject();
                    if(element.has("rotation")){
                        var r=element.getAsJsonObject("rotation");
                        if(!r.has("axis")||!r.has("angle")||!VANILLA_ANGLES.contains(r.get("angle").getAsDouble()))
                            offenders.add(path+" 元素 "+i+": "+r);
                    }
                    i++;
                }
            }
        }
        assertTrue(offenders.isEmpty(),"这些模型 1.20.1 读不出来：\n"+String.join("\n",offenders));
        assertTrue(loaders>0,"乐器模型里有任意角度的转动，应该有用 "+ROTATED_LOADER+" 的模型");
    }
}
