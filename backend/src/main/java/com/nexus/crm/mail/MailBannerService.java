package com.nexus.crm.mail;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * The image at the end of every email sent from the CRM. An admin sets it up in one of
 * two ways: a finished banner, sent exactly as uploaded; or a company logo, which is laid
 * out beside each sender's own name and address when they send.
 */
@Service
@RequiredArgsConstructor
public class MailBannerService {

	private static final UUID AUDIT_ID = new UUID(0, 1);

	/**
	 * Drawn at twice the size it is shown in the email (SHOWN_WIDTH), so it stays sharp
	 * on dense screens.
	 */
	static final int WIDTH = 1200, HEIGHT = 300;

	public static final int SHOWN_WIDTH = 520;

	private final JdbcClient jdbc;

	private final AuditService audit;

	public record Banner(boolean present, String kind, String companyName, String previewDataUrl) {
	}

	/** The image to attach and the text a mail client shows when images are blocked. */
	public record Rendered(byte[] png, String alt) {
	}

	private record Saved(byte[] image, String kind, String companyName) {
	}

	public Banner status(UUID userId) {
		Saved saved = saved();
		Rendered rendered = render(userId);
		if (saved == null || rendered == null) {
			return new Banner(false, null, null, null);
		}
		return new Banner(true, saved.kind(), saved.companyName(),
				"data:image/png;base64," + Base64.getEncoder().encodeToString(rendered.png()));
	}

	@Transactional
	public void upload(UUID adminId, String base64, String kind, String companyName) {
		boolean logo = "logo".equals(kind);
		if (!logo && kind != null && !"banner".equals(kind)) {
			throw invalid("Choose a banner or a logo.");
		}
		String company = companyName == null || companyName.isBlank() ? null : companyName.strip();
		if (company != null && company.length() > 80) {
			throw invalid("Use a company name of 80 characters or fewer.");
		}
		if (base64 == null || base64.length() > 2_800_000) {
			throw invalid("Choose a PNG or JPEG under 2 MB.");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64);
		}
		catch (IllegalArgumentException e) {
			throw invalid("Choose a valid PNG or JPEG.");
		}
		if (bytes.length > 2_000_000) {
			throw invalid("Choose a PNG or JPEG under 2 MB.");
		}

		try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext()) {
				throw invalid("Choose a PNG or JPEG image.");
			}
			ImageReader reader = readers.next();
			try {
				String format = reader.getFormatName();
				if (!"png".equalsIgnoreCase(format) && !"jpeg".equalsIgnoreCase(format)) {
					throw invalid("Choose a PNG or JPEG image.");
				}
				reader.setInput(stream);
				int width = reader.getWidth(0), height = reader.getHeight(0);
				if (logo && (width < 64 || width > 2000 || height < 64 || height > 2000)) {
					throw invalid("The logo must be between 64 and 2000 px on each side.");
				}
				if (!logo && (width < 600 || width > 2400 || height < 100 || height > 800)) {
					throw invalid("The banner must be 600–2400 px wide and 100–800 px tall.");
				}
				// Re-encoding keeps only the pixels: anything else hidden in the file is
				// dropped.
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write(reader.read(0), "png", output);
				// A logo is also kept on its own (logo_png) and survives a later switch
				// to a finished banner: exports use it.
				jdbc.sql("insert into mail_banner (id,image_png,kind,company_name,logo_png,updated_by) "
						+ "values (1,:image,:kind,:company,case when :kind='logo' then :image end,:user) "
						+ "on conflict (id) do update set image_png=excluded.image_png,kind=excluded.kind,"
						+ "company_name=coalesce(excluded.company_name,mail_banner.company_name),"
						+ "logo_png=coalesce(excluded.logo_png,mail_banner.logo_png),updated_by=excluded.updated_by,updated_at=now()")
					.param("image", output.toByteArray())
					.param("kind", logo ? "logo" : "banner")
					.param("company", company)
					.param("user", adminId)
					.update();
				audit.record(adminId, "MailBanner", AUDIT_ID, AuditLog.Action.UPDATE, null);
			}
			finally {
				reader.dispose();
			}
		}
		catch (IOException e) {
			throw invalid("The image could not be read.");
		}
	}

	@Transactional
	public void remove(UUID adminId) {
		if (jdbc.sql("delete from mail_banner where id=1").update() > 0) {
			audit.record(adminId, "MailBanner", AUDIT_ID, AuditLog.Action.DELETE, null);
		}
	}

	/**
	 * The company's logo and name, for anything that carries the brand. Either may be
	 * null.
	 */
	public record Brand(byte[] logoPng, String companyName) {
	}

	public Brand brand() {
		return jdbc.sql("select logo_png,company_name from mail_banner where id=1")
			.query((rs, n) -> new Brand(rs.getBytes("logo_png"), rs.getString("company_name")))
			.optional()
			.orElse(new Brand(null, null));
	}

	/** The image for this sender, or null when no banner is set up. */
	public Rendered render(UUID userId) {
		Saved saved = saved();
		if (saved == null) {
			return null;
		}
		var sender = jdbc.sql("select display_name,job_title,email from app_user where id=:user")
			.param("user", userId)
			.query((rs,
					n) -> new String[] { rs.getString("display_name"), rs.getString("email"),
							rs.getString("job_title") })
			.single();
		try {
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(saved.image()));
			boolean logo = "logo".equals(saved.kind());
			BufferedImage image = logo ? signature(source, sender[0], sender[2], saved.companyName(), sender[1])
					: flatten(source);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(image, "png", output);
			String alt = logo ? sender[0] + (saved.companyName() == null ? "" : ", " + saved.companyName())
					: "Email signature";
			return new Rendered(output.toByteArray(), alt);
		}
		catch (IOException e) {
			throw new IllegalStateException("Saved mail banner could not be read.", e);
		}
	}

	private Saved saved() {
		return jdbc.sql("select image_png,kind,company_name from mail_banner where id=1")
			.query((rs, n) -> new Saved(rs.getBytes("image_png"), rs.getString("kind"), rs.getString("company_name")))
			.optional()
			.orElse(null);
	}

	/**
	 * The image on a white background. Problem we had: a PNG with transparent areas was
	 * drawn onto a black canvas, so dark artwork and text disappeared in the recipient's
	 * inbox.
	 */
	static BufferedImage flatten(BufferedImage source) {
		BufferedImage canvas = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = canvas.createGraphics();
		try {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
			g.drawImage(source, 0, 0, null);
		}
		finally {
			g.dispose();
		}
		return canvas;
	}

	/**
	 * A signature card: the logo on the left, a divider, then the sender's name, job
	 * title, company and address.
	 */
	static BufferedImage signature(BufferedImage logo, String name, String jobTitle, String company, String email) {
		BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = canvas.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, WIDTH, HEIGHT);
			g.setColor(new Color(0xE5E7EB));
			g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
			g.drawRect(1, 1, WIDTH - 3, HEIGHT - 3);

			// Logo: as large as fits in a 300 x 180 box, never enlarged beyond 2x,
			// centred in the box.
			int boxX = 56, boxWidth = 300, boxHeight = 180;
			double scale = Math.min(2,
					Math.min((double) boxWidth / logo.getWidth(), (double) boxHeight / logo.getHeight()));
			int logoWidth = (int) Math.round(logo.getWidth() * scale),
					logoHeight = (int) Math.round(logo.getHeight() * scale);
			g.drawImage(logo, boxX + (boxWidth - logoWidth) / 2, (HEIGHT - logoHeight) / 2, logoWidth, logoHeight,
					null);

			int dividerX = boxX + boxWidth + 44;
			g.setColor(new Color(0xD7DCE3));
			g.fillRect(dividerX, 66, 2, HEIGHT - 132);

			int textX = dividerX + 46, textWidth = WIDTH - textX - 56;
			// Job title and company share one line: "Software Engineer · Webknot
			// Technologies".
			String role = java.util.stream.Stream.of(jobTitle, company)
				.filter(part -> part != null && !part.isBlank())
				.map(String::strip)
				.collect(java.util.stream.Collectors.joining("  ·  "));
			boolean hasCompany = !role.isEmpty();
			int nameBaseline = hasCompany ? 122 : 138;

			g.setColor(new Color(0x111827));
			g.setFont(fit(g, name, Font.BOLD, 54, 30, textWidth));
			g.drawString(clip(g, name, textWidth), textX, nameBaseline);

			g.setColor(accent(logo));
			g.fillRoundRect(textX, nameBaseline + 22, 72, 5, 5, 5);

			int next = nameBaseline + 74;
			if (hasCompany) {
				g.setColor(new Color(0x334155));
				g.setFont(fit(g, role, Font.PLAIN, 30, 22, textWidth));
				g.drawString(clip(g, role, textWidth), textX, next);
				next += 42;
			}
			g.setColor(new Color(0x64748B));
			g.setFont(fit(g, email, Font.PLAIN, 25, 18, textWidth));
			g.drawString(clip(g, email, textWidth), textX, next);
		}
		finally {
			g.dispose();
		}
		return canvas;
	}

	/**
	 * The largest font from `size` down to `smallest` at which the text fits the width.
	 */
	private static Font fit(Graphics2D g, String text, int style, int size, int smallest, int width) {
		Font font = new Font(Font.SANS_SERIF, style, size);
		while (size > smallest && g.getFontMetrics(font).stringWidth(text) > width) {
			font = font.deriveFont((float) --size);
		}
		return font;
	}

	/**
	 * The text, shortened with an ellipsis when it is too wide even at the smallest font.
	 */
	private static String clip(Graphics2D g, String text, int width) {
		if (g.getFontMetrics().stringWidth(text) <= width) {
			return text;
		}
		String clipped = text;
		while (clipped.length() > 1 && g.getFontMetrics().stringWidth(clipped + "…") > width) {
			clipped = clipped.substring(0, clipped.length() - 1);
		}
		return clipped.stripTrailing() + "…";
	}

	/**
	 * The logo's own colour, for the small rule under the name: the average of its vivid
	 * pixels. Slate when it has none.
	 */
	static Color accent(BufferedImage logo) {
		long red = 0, green = 0, blue = 0, count = 0;
		int step = Math.max(1, Math.max(logo.getWidth(), logo.getHeight()) / 120);
		for (int y = 0; y < logo.getHeight(); y += step) {
			for (int x = 0; x < logo.getWidth(); x += step) {
				int argb = logo.getRGB(x, y);
				if ((argb >>> 24) < 128) {
					continue;
				}
				int r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
				float[] hsb = Color.RGBtoHSB(r, gr, b, null);
				if (hsb[1] < .45f || hsb[2] < .25f) {
					continue;
				}
				red += r;
				green += gr;
				blue += b;
				count++;
			}
		}
		return count == 0 ? new Color(0x475569)
				: new Color((int) (red / count), (int) (green / count), (int) (blue / count));
	}

	private ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_FAILED, message);
	}

}
