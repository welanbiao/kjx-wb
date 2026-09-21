package org.dromara.common.core.utils.ip;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.file.FileUtils;
import org.lionsoul.ip2region.xdb.Searcher;

import java.io.File;

/**
 * 根据ip地址定位工具类，离线方式。
 * 来源：RuoYi-Vue-Plus v5.2.3 {@code org.dromara.common.core.utils.ip.RegionUtils}。
 * KJX 适配：ip2region.xdb 未随 common 裁剪导入时降级为「未知」，避免静态初始化拖垮启动。
 *
 * @author lishuyan
 */
@Slf4j
public class RegionUtils {

    private static final Searcher SEARCHER = loadSearcher();

    private static Searcher loadSearcher() {
        String fileName = "/ip2region.xdb";
        try {
            File existFile = FileUtils.file(FileUtil.getTmpDir() + FileUtil.FILE_SEPARATOR + fileName);
            if (!FileUtils.exist(existFile)) {
                ClassPathResource fileStream = new ClassPathResource(fileName);
                if (ObjectUtil.isEmpty(fileStream.getStream())) {
                    log.warn("ip2region.xdb 不在 classpath，RegionUtils 降级为未知地址");
                    return null;
                }
                FileUtils.writeFromStream(fileStream.getStream(), existFile);
            }
            byte[] cBuff = Searcher.loadContentFromFile(existFile.getPath());
            return Searcher.newWithBuffer(cBuff);
        } catch (Exception e) {
            log.warn("RegionUtils 初始化失败，降级为未知地址: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据IP地址离线获取城市
     */
    public static String getCityInfo(String ip) {
        if (SEARCHER == null) {
            return "未知";
        }
        try {
            ip = ip.trim();
            String region = SEARCHER.search(ip);
            return region.replace("0|", "").replace("|0", "");
        } catch (Exception e) {
            log.error("IP地址离线获取城市异常 {}", ip);
            return "未知";
        }
    }
}
