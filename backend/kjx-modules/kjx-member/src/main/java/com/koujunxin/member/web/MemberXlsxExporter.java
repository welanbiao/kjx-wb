package com.koujunxin.member.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.koujunxin.member.domain.MemberRecord;
import com.koujunxin.member.normalize.ExpireTimes;

/**
 * 最小 XLSX（无 POI 依赖）。仅导出本部门已授权数据。
 */
public final class MemberXlsxExporter {

    private MemberXlsxExporter() {
    }

    public static byte[] export(List<MemberRecord> rows) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", contentTypes());
                put(zip, "_rels/.rels", rels());
                put(zip, "xl/workbook.xml", workbook());
                put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
                put(zip, "xl/worksheets/sheet1.xml", sheet(rows));
            }
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("xlsx export failed", ex);
        }
    }

    private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """;
    }

    private static String rels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private static String workbook() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="members" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """;
    }

    private static String workbookRels() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
                """;
    }

    private static String sheet(List<MemberRecord> rows) {
        StringBuilder xml = new StringBuilder();
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                """);
        xml.append(rowXml(1, List.of("memberCode", "douyinId", "subscribedMonths", "subscribedDays",
                "expireAt", "availableCount", "status", "bindState")));
        int r = 2;
        for (MemberRecord member : rows) {
            xml.append(rowXml(r++, List.of(
                    member.memberCode(),
                    member.douyinId(),
                    String.valueOf(member.subscribedMonths()),
                    String.valueOf(member.subscribedDays()),
                    String.valueOf(ExpireTimes.format(member.expireAt())),
                    String.valueOf(member.availableCount()),
                    member.status(),
                    String.valueOf(member.bindState()))));
        }
        xml.append("</sheetData></worksheet>");
        return xml.toString();
    }

    private static String rowXml(int row, List<String> cells) {
        StringBuilder xml = new StringBuilder("<row r=\"").append(row).append("\">");
        char col = 'A';
        for (String cell : cells) {
            xml.append("<c r=\"").append(col).append(row).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(escape(cell)).append("</t></is></c>");
            col++;
        }
        return xml.append("</row>").toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
