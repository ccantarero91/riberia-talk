package com.lambdarat.riberia

import cats.effect.IO

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/** Caps the longest side of the photo to spend fewer tokens on the vision model. */
object ImagePrep:

  def prepare(bytes: Array[Byte], maxSide: Int = 1024): IO[String] =
    IO.blocking {
      val img    = ImageIO.read(new java.io.ByteArrayInputStream(bytes))
      val scaled = shrink(img, maxSide)

      val out = new ByteArrayOutputStream()
      ImageIO.write(scaled, "jpg", out)
      Base64.getEncoder.encodeToString(out.toByteArray)
    }

  private def shrink(img: BufferedImage, maxSide: Int): BufferedImage =
    val scale = maxSide.toDouble / math.max(img.getWidth, img.getHeight)
    if scale >= 1 then toRgb(img)
    else
      val w      = math.max(1, (img.getWidth * scale).toInt)
      val h      = math.max(1, (img.getHeight * scale).toInt)
      val target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
      val g      = target.createGraphics()
      g.drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null)
      g.dispose()
      target

  // JPEG has no alpha channel
  private def toRgb(img: BufferedImage): BufferedImage =
    val target = new BufferedImage(img.getWidth, img.getHeight, BufferedImage.TYPE_INT_RGB)
    val g      = target.createGraphics()
    g.drawImage(img, 0, 0, null)
    g.dispose()
    target
