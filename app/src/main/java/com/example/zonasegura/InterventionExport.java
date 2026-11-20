package com.example.zonasegura;

import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.util.Xml;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.xmlpull.v1.XmlSerializer;

final class InterventionExport {
    static void pdf(List<String[]> rows, OutputStream out) throws IOException {
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        PdfDocument doc = new PdfDocument();
        try {
            for (int start = 1, page = 1; start < Math.max(2, rows.size()); start += 34, page++) {
                PdfDocument.Page sheet = doc.startPage(new PdfDocument.PageInfo.Builder(595,842,page).create());
                pen.setTextSize(19);
                sheet.getCanvas().drawText("Zona Segura · Intervenciones",36,52,pen);
                pen.setTextSize(12);
                for (int line = 0; line <= 34; line++) {
                    int index = line == 0 ? 0 : start + line - 1;
                    if (index >= rows.size()) break;
                    String[] row = rows.get(index);
                    for (int col = 0; col < 3; col++)
                        sheet.getCanvas().drawText(row[col],36 + col * 165,90 + line * 20,pen);
                }
                doc.finishPage(sheet);
            }
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static void excel(List<String[]> rows, OutputStream out) throws IOException {
        String rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        String main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        String relationships = "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>";
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            entry(zip,"[Content_Types].xml","<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                    + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                    + "<Override PartName='/xl/workbook.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml'/>"
                    + "<Override PartName='/xl/worksheets/sheet1.xml' ContentType='application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml'/></Types>");
            entry(zip,"_rels/.rels",relationships+"<Relationship Id='r1' Type='"+rel+"/officeDocument' Target='xl/workbook.xml'/></Relationships>");
            entry(zip,"xl/workbook.xml","<workbook xmlns='"+main+"' xmlns:r='"+rel+"'><sheets><sheet name='Intervenciones' sheetId='1' r:id='r1'/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels",relationships+"<Relationship Id='r1' Type='"+rel+"/worksheet' Target='worksheets/sheet1.xml'/></Relationships>");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(zip,"UTF-8");
            xml.setPrefix("",main);
            xml.startTag(main,"worksheet").startTag(main,"sheetData");
            for (String[] row : rows) {
                xml.startTag(main,"row");
                for (String cell : row) {
                    xml.startTag(main,"c").attribute(null,"t","inlineStr").startTag(main,"is")
                            .startTag(main,"t").text(cell).endTag(main,"t").endTag(main,"is").endTag(main,"c");
                }
                xml.endTag(main,"row");
            }
            xml.endTag(main,"sheetData").endTag(main,"worksheet");
            xml.flush();
            zip.closeEntry();
        }
    }
}
