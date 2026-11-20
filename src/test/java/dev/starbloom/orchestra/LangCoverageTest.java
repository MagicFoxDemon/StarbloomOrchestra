package dev.starbloom.orchestra;

import com.google.gson.*;
import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 每个方块/物品都必须有中英文名字。
 *
 * <p>灵感来自实机反馈："蘑菇的名字显示有问题"。那次查下来蘑菇本身是有名字的，
 * 但顺带发现 `mercury` 与 `molten_glass` 两个方块从来没有翻译——游戏里会直接把
 * 翻译键（`block.starbloom_orchestra.mercury`）显示出来。这个测试把这一类问题钉住。
 *
 * <p>测试类路径里的资源可能是目录（build/resources/main）也可能被打进 jar，
 * 所以枚举注册 ID 时两种形式都要处理（{@link ItemModelDisplayTest#names}）。
 */
class LangCoverageTest {
    private static final String ROOT="assets/starbloom_orchestra/";

    private JsonObject lang(String locale)throws IOException{
        try(var input=LangCoverageTest.class.getResourceAsStream("/"+ROOT+"lang/"+locale+".json")){
            assertNotNull(input,locale+" 语言文件不存在");
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * 注册项 ID：blockstates/ 里的方块和 models/item/ 里的物品（1.21.1 没有 items/ 物品模型定义，物品 `<id>` 用 `models/item/<id>.json`；
     * 只供渲染的模型不是物品，见 {@link ItemModelDisplayTest#renderOnly}）。目录、jar 两种资源形态都由 {@link ItemModelDisplayTest#names} 处理。
     */
    private Set<String> registeredIds()throws Exception{
        var ids=new TreeSet<String>();
        for(var folder:folderIds().values())ids.addAll(folder);
        return ids;
    }

    /** 每个物品（models/item/）都要有 `item.` 名字，blockstates/ 下的每个条目都要有 `block.` 名字。 */
    @Test void everyBlockAndItemHasAName()throws Exception{
        var ids=registeredIds();
        assertTrue(ids.size()>20,"注册项数量异常："+ids.size());
        for(String locale:new String[]{"zh_cn","en_us"}){
            var lang=lang(locale);
            var missingItems=new ArrayList<String>();
            var missingBlocks=new ArrayList<String>();
            for(var folder:folderIds().entrySet()){
                var missing=folder.getKey().equals("items")?missingItems:missingBlocks;
                String prefix=folder.getKey().equals("items")?"item.":"block.";
                for(String id:folder.getValue())
                    if(!lang.has(prefix+"starbloom_orchestra."+id))missing.add(id);
            }
            assertTrue(missingItems.isEmpty(),locale+" 缺少物品名字（item.）："+missingItems);
            assertTrue(missingBlocks.isEmpty(),locale+" 缺少方块名字（block.）："+missingBlocks);
        }
    }

    /** 分别列出 blockstates/ 里的方块与 models/item/ 里的物品 ID。 */
    private static Map<String,Set<String>> folderIds()throws Exception{
        var cache=IDS;
        if(cache!=null)return cache;
        var map=new LinkedHashMap<String,Set<String>>();
        map.put("blockstates",new TreeSet<>(ItemModelDisplayTest.names("blockstates")));
        map.put("items",new TreeSet<>(ItemModelDisplayTest.itemIds()));
        IDS=map;
        return map;
    }

    private static Map<String,Set<String>> IDS;

    /** 名字不能是空串，也不能原样回退成翻译键。 */
    @Test void namesAreNotBlank()throws Exception{
        for(String locale:new String[]{"zh_cn","en_us"}){
            for(var entry:lang(locale).entrySet()){
                if(!entry.getKey().startsWith("block.starbloom_orchestra.")
                    &&!entry.getKey().startsWith("item.starbloom_orchestra."))continue;
                String value=entry.getValue().getAsString();
                assertFalse(value.isBlank(),locale+" 里 "+entry.getKey()+" 是空名字");
                assertNotEquals(entry.getKey(),value,locale+" 里 "+entry.getKey()+" 回退成了翻译键");
            }
        }
    }
}
