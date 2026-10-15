package com.lambdarat.riberia

import cats.effect.IO
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.nio.JpegWriter

import java.util.Base64

/** Caps the longest side of the photo to spend fewer tokens on the vision model. */
object ImagePrep:

  def prepare(bytes: Array[Byte], maxSide: Int = 1024): IO[String] =
    IO.blocking {
      val jpeg = ImmutableImage.loader()
        .fromBytes(bytes)              // also fixes the EXIF orientation of phone photos
        .bound(maxSide, maxSide)       // longest side ≤ maxSide, never upscales
        .bytes(JpegWriter.compression(85))
      Base64.getEncoder.encodeToString(jpeg)
    }
