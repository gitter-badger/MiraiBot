package com.windowx.miraibot;

import sun.font.FontDesignMetrics;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WordToImage {
	public static byte[] createImage(String content, Font font, int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
		Graphics g = image.getGraphics();
		g.setClip(0, 0, width, height);
		g.setColor(Color.white);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.black);
		g.setFont(font);
		FontMetrics fm = FontDesignMetrics.getMetrics(font);
		int stringWidth = fm.stringWidth(content);
		int stringHeight = fm.getHeight();
		int ascent = fm.getAscent();
		int left = (width - stringWidth) / 2;
		int top = (height - stringHeight) / 2 + ascent;
		g.drawString(content, left, top);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "jpg", out);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return out.toByteArray();
	}
}
