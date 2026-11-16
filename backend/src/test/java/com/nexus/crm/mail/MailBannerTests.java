package com.nexus.crm.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class MailBannerTests {

	/** A blue mark on a fully transparent background, like a real logo file. */
	private static BufferedImage logo() {
		BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x2563EB));
		g.setStroke(new BasicStroke(44, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawOval(40, 60, 170, 170);
		g.drawOval(190, 60, 170, 170);
		g.dispose();
		return image;
	}

	/** The bug this guards: transparent areas came out black in the recipient's inbox. */
	@Test
	void transparentAreasBecomeWhite() {
		BufferedImage flat = MailBannerService.flatten(logo());

		assertThat(flat.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);
		assertThat(flat.getRGB(40, 145) & 0xFFFFFF).isEqualTo(0x2563EB);
	}

	@Test
	void signatureIsAWhiteCardThatTakesItsAccentFromTheLogo() throws IOException {
		BufferedImage card = MailBannerService.signature(logo(), "Likhith Raju", "Software Engineer", "Webknot Technologies", "likhith@example.com");
		// Kept as a build artefact so the layout can be looked at: target/mail-signature-sample.png
		ImageIO.write(card, "png", new File("target/mail-signature-sample.png"));

		assertThat(card.getWidth()).isEqualTo(MailBannerService.WIDTH);
		assertThat(card.getHeight()).isEqualTo(MailBannerService.HEIGHT);
		assertThat(card.getRGB(20, 20) & 0xFFFFFF).isEqualTo(0xFFFFFF);
		// Soft edge pixels shift the average by a shade, so compare loosely.
		Color accent = MailBannerService.accent(logo());
		assertThat(accent.getBlue()).isGreaterThan(220);
		assertThat(accent.getRed()).isLessThan(60);
	}

	@Test
	void aVeryLongNameIsShortenedInsteadOfRunningOffTheCard() {
		String name = "Maximiliana Alexandrovna Konstantinopolskaya-Wolfeschlegelsteinhausen the Third of Somewhere";
		BufferedImage card = MailBannerService.signature(logo(), name, null, null, "someone.with.a.long.address@a-long-company-domain.example.com");

		// The right-hand margin stays empty (rows 0-1 and the last two are the card's border).
		for (int y = 2; y < card.getHeight() - 2; y++) assertThat(card.getRGB(MailBannerService.WIDTH - 30, y) & 0xFFFFFF).isEqualTo(0xFFFFFF);
	}
}
