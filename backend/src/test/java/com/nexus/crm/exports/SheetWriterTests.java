package com.nexus.crm.exports;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

class SheetWriterTests {

	private static byte[] logo() throws IOException {
		BufferedImage image = new BufferedImage(300, 120, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x2563EB));
		g.fillRoundRect(10, 10, 280, 100, 40, 40);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private static Map<String, byte[]> unzip(byte[] file) throws IOException {
		Map<String, byte[]> parts = new HashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) parts.put(entry.getName(), zip.readAllBytes());
		}
		return parts;
	}

	@Test
	void everyPartIsWellFormedAndTheLogoIsEmbedded() throws Exception {
		byte[] logo = logo();
		List<List<Object>> rows = List.of(
			Arrays.asList("Asha & Sons <Pvt>", "=HYPERLINK(\"http://evil\")", 125000.5, null),
			Arrays.asList("Second \u0001row", "Qualified", 7, "2026-11-16"));
		byte[] file = SheetWriter.write("Leads", "Webknot Technologies · Exported 16 Nov 2026 by Asha · 2 rows", List.of("Name", "Status", "Value", "Created"), rows, logo);
		// Kept as a build artefact so the file can be opened and looked at: target/export-sample.xlsx
		Files.write(Path.of("target/export-sample.xlsx"), file);

		Map<String, byte[]> parts = unzip(file);
		assertThat(parts.keySet()).contains("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/drawings/drawing1.xml");
		assertThat(parts.get("xl/media/image1.png")).isEqualTo(logo);
		for (var part : parts.entrySet()) {
			if (part.getKey().endsWith(".png")) continue;
			DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(part.getValue()));
		}

		String sheet = new String(parts.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
		// A value that looks like a formula is text, not a formula.
		assertThat(sheet).contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">=HYPERLINK(").doesNotContain("<f>");
		assertThat(sheet).contains("Asha &amp; Sons &lt;Pvt&gt;").contains("<v>125000.5</v>").contains("Second row");
		// Header on row 8 (below the logo), frozen, with a filter over the table.
		assertThat(sheet).contains("ySplit=\"8\"").contains("<autoFilter ref=\"A8:D10\"/>");
	}

	@Test
	void worksWithoutALogo() throws Exception {
		Map<String, byte[]> parts = unzip(SheetWriter.write("Team", "Exported today", List.of("Name"), List.of(List.<Object>of("Asha")), null));

		assertThat(parts.keySet()).doesNotContain("xl/drawings/drawing1.xml", "xl/media/image1.png");
		assertThat(new String(parts.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8)).contains("ySplit=\"4\"").doesNotContain("<drawing");
	}

	@Test
	void columnNamesGoPastZ() {
		assertThat(SheetWriter.column(0)).isEqualTo("A");
		assertThat(SheetWriter.column(25)).isEqualTo("Z");
		assertThat(SheetWriter.column(26)).isEqualTo("AA");
		assertThat(SheetWriter.column(39)).isEqualTo("AN");
	}
}
