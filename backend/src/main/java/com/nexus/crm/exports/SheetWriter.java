package com.nexus.crm.exports;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

/**
 * Writes one styled worksheet as an Excel file (.xlsx): the company logo, a title, a line saying who
 * exported it and when, then the table with a frozen, filterable header.
 *
 * <p>
 * An .xlsx file is a zip of XML parts, so this needs nothing beyond the JDK. Text is written as
 * inline strings, never as formulas: a cell that starts with "=" stays text when the file is opened.
 */
final class SheetWriter {

  private static final int LOGO_HEIGHT_PX = 56, LOGO_MAX_WIDTH_PX = 260, EMU_PER_PX = 9525;
  private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
  private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

  private SheetWriter() { }

  /** Cells are Numbers (written as numbers) or anything else (written as text). logoPng may be null. */
  static byte[] write(String title, String subtitle, List<String> header, List<List<Object>> rows, byte[] logoPng) {
    BufferedImage logo = read(logoPng);
    // With a logo: rows 1-3 hold it, title on 5, subtitle on 6, header on 8. Without: 1, 2 and 4.
    int titleRow = logo == null ? 1 : 5, headerRow = titleRow + 3;
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
        put(zip, "[Content_Types].xml", XML + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
          + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
          + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
          + (logo == null ? "" : "<Default Extension=\"png\" ContentType=\"image/png\"/>")
          + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
          + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
          + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
          + (logo == null ? "" : "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
          + "</Types>");
        put(zip, "_rels/.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" Type=\"" + REL + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        put(zip, "xl/workbook.xml", XML + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"" + REL + "\">"
          + "<sheets><sheet name=\"" + escape(sheetName(title)) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        put(zip, "xl/_rels/workbook.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
          + "<Relationship Id=\"rId1\" Type=\"" + REL + "/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
          + "<Relationship Id=\"rId2\" Type=\"" + REL + "/styles\" Target=\"styles.xml\"/></Relationships>");
        put(zip, "xl/styles.xml", STYLES);
        put(zip, "xl/worksheets/sheet1.xml", sheet(title, subtitle, header, rows, titleRow, headerRow, logo != null));
        if (logo != null) {
          put(zip, "xl/worksheets/_rels/sheet1.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + REL + "/drawing\" Target=\"../drawings/drawing1.xml\"/></Relationships>");
          put(zip, "xl/drawings/drawing1.xml", drawing(logo));
          put(zip, "xl/drawings/_rels/drawing1.xml.rels", XML + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"" + REL + "/image\" Target=\"../media/image1.png\"/></Relationships>");
          zip.putNextEntry(new ZipEntry("xl/media/image1.png"));
          zip.write(logoPng);
          zip.closeEntry();
        }
      }
      return bytes.toByteArray();
    } catch (IOException e) { throw new IllegalStateException("The spreadsheet could not be written.", e); }
  }

  /* Styles, by index in cellXfs: 0 plain, 1 title, 2 subtitle, 3 table header, 4 text cell, 5 number cell. */
  private static final String STYLES = XML + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
    + "<fonts count=\"4\">"
    + "<font><sz val=\"11\"/><color rgb=\"FF1F2933\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
    + "<font><b/><sz val=\"16\"/><color rgb=\"FF111827\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
    + "<font><sz val=\"10\"/><color rgb=\"FF64748B\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
    + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
    + "</fonts>"
    + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
    + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1E293B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
    + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
    + "<border><left/><right/><top/><bottom style=\"thin\"><color rgb=\"FFE2E8F0\"/></bottom><diagonal/></border></borders>"
    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
    + "<cellXfs count=\"6\">"
    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
    + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
    + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\"/></xf>"
    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\"/></xf>"
    + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"right\" vertical=\"center\"/></xf>"
    + "</cellXfs>"
    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";

  private static String sheet(String title, String subtitle, List<String> header, List<List<Object>> rows, int titleRow, int headerRow, boolean hasLogo) {
    int columns = header.size();
    String lastColumn = column(columns - 1);
    StringBuilder xml = new StringBuilder(XML).append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"").append(REL).append("\">")
      // Printing and PDF export: landscape, as wide as one page, however long.
      .append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr>")
      .append("<sheetViews><sheetView showGridLines=\"0\" workbookViewId=\"0\"><pane ySplit=\"").append(headerRow).append("\" topLeftCell=\"A").append(headerRow + 1)
      .append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
      .append("<sheetFormatPr defaultRowHeight=\"18\"/><cols>");
    for (int c = 0; c < columns; c++) {
      int widest = header.get(c).length();
      for (List<Object> row : rows) if (c < row.size() && row.get(c) != null) widest = Math.max(widest, text(row.get(c)).length());
      // The first column also carries the title and the logo, so it is never narrow.
      int width = Math.clamp(widest + 3, c == 0 ? 24 : 11, 60);
      xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
    }
    xml.append("</cols><sheetData>");
    if (hasLogo) for (int r = 1; r <= 3; r++) xml.append("<row r=\"").append(r).append("\" ht=\"18\" customHeight=\"1\"/>");
    xml.append("<row r=\"").append(titleRow).append("\" ht=\"26\" customHeight=\"1\">").append(textCell("A" + titleRow, title, 1)).append("</row>");
    xml.append("<row r=\"").append(titleRow + 1).append("\">").append(textCell("A" + (titleRow + 1), subtitle, 2)).append("</row>");
    xml.append("<row r=\"").append(headerRow).append("\" ht=\"24\" customHeight=\"1\">");
    for (int c = 0; c < columns; c++) xml.append(textCell(column(c) + headerRow, header.get(c), 3));
    xml.append("</row>");
    int r = headerRow;
    for (List<Object> row : rows) {
      r++;
      xml.append("<row r=\"").append(r).append("\" ht=\"20\" customHeight=\"1\">");
      for (int c = 0; c < columns; c++) {
        Object value = c < row.size() ? row.get(c) : null;
        String ref = column(c) + r;
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) xml.append("<c r=\"").append(ref).append("\" s=\"5\"><v>").append(number).append("</v></c>");
        else xml.append(textCell(ref, value == null ? "" : text(value), 4));
      }
      xml.append("</row>");
    }
    xml.append("</sheetData>");
    xml.append("<autoFilter ref=\"A").append(headerRow).append(':').append(lastColumn).append(Math.max(r, headerRow)).append("\"/>");
    xml.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>");
    xml.append("<pageSetup orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/>");
    if (hasLogo) xml.append("<drawing r:id=\"rId1\"/>");
    return xml.append("</worksheet>").toString();
  }

  /** The logo at the top-left corner, 56px tall (narrower when it is very wide), keeping its proportions. */
  private static String drawing(BufferedImage logo) {
    double scale = Math.min((double) LOGO_HEIGHT_PX / logo.getHeight(), (double) LOGO_MAX_WIDTH_PX / logo.getWidth());
    long width = Math.round(logo.getWidth() * scale) * EMU_PER_PX, height = Math.round(logo.getHeight() * scale) * EMU_PER_PX;
    return XML + "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">"
      + "<xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>57150</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>57150</xdr:rowOff></xdr:from>"
      + "<xdr:ext cx=\"" + width + "\" cy=\"" + height + "\"/>"
      + "<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"2\" name=\"Logo\" descr=\"Company logo\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>"
      + "<xdr:blipFill><a:blip xmlns:r=\"" + REL + "\" r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>"
      + "<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + width + "\" cy=\"" + height + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr>"
      + "</xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>";
  }

  private static String textCell(String ref, String value, int style) {
    return "<c r=\"" + ref + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escape(value) + "</t></is></c>";
  }

  /** A cell's text, at most 2000 characters. */
  private static String text(Object value) {
    String text = String.valueOf(value);
    return text.length() <= 2000 ? text : text.substring(0, 2000) + "…";
  }

  /** 0 is "A", 25 is "Z", 26 is "AA". */
  static String column(int index) {
    StringBuilder name = new StringBuilder();
    for (int n = index; n >= 0; n = n / 26 - 1) name.insert(0, (char) ('A' + n % 26));
    return name.toString();
  }

  /** Excel allows 31 characters in a sheet name and none of \ / ? * [ ] : */
  private static String sheetName(String title) {
    String name = title.replaceAll("[\\\\/?*\\[\\]:]", " ").strip();
    return name.isEmpty() ? "Export" : name.substring(0, Math.min(31, name.length()));
  }

  /** XML-safe text: markup characters escaped, and characters XML 1.0 forbids removed. */
  static String escape(String value) {
    StringBuilder out = new StringBuilder(value.length() + 16);
    value.codePoints().forEach(c -> {
      boolean allowed = c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD) || c >= 0x10000;
      if (!allowed) return;
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        default -> out.appendCodePoint(c);
      }
    });
    return out.toString();
  }

  private static BufferedImage read(byte[] png) {
    if (png == null) return null;
    try { return ImageIO.read(new ByteArrayInputStream(png)); }
    catch (IOException e) { return null; }
  }

  private static void put(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
