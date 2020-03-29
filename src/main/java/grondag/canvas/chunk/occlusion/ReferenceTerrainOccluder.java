package grondag.canvas.chunk.occlusion;

import java.io.File;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceImpl;

import grondag.canvas.CanvasMod;
import grondag.canvas.Configurator;

// Some elements are adapted from content found at
// https://fgiesen.wordpress.com/2013/02/17/optimizing-sw-occlusion-culling-index/
// by Fabian “ryg” Giesen. That content is in the public domain.

public class ReferenceTerrainOccluder extends ClippingTerrainOccluder {
	@SuppressWarnings("unused")
	private void drawTriReference(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		if (!prepareTriBounds(v0, v1, v2)) {
			return;
		}

		prepareTriScan();

		// Triangle setup
		final int a0 = this.a0;
		final int b0 = this.b0;
		final int a1 = this.a1;
		final int b1 = this.b1;
		final int a2 = this.a2;
		final int b2 = this.b2;

		// Barycentric coordinates at bounding box origin
		int w0_row = wOrigin0;
		int w1_row = wOrigin1;
		int w2_row = wOrigin2;

		// Rasterize
		for (int y = minPixelY; y <= maxPixelY; y++) {
			// Barycentric coordinates
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;

			for (int x = minPixelX; x <= maxPixelX; x++) {
				if ((w0 | w1 | w2) >= 0) {
					drawPixel(x, y);
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
			}

			// One row step
			w0_row += b0;
			w1_row += b1;
			w2_row += b2;
		}
	}

	@Override
	protected void drawTri(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		if (!prepareTriBounds(v0, v1, v2)) {
			return;
		}

		if (triNeedsClipped()) {
			drawClippedLowX(v0, v1, v2);
			return;
		}

		prepareTriScan();

		// Triangle setup
		final int px0 = minPixelX;
		final int py0 = minPixelY;
		final int px1 = maxPixelX;
		final int py1 = maxPixelY;

		final int bx0 = px0 >> BIN_AXIS_SHIFT;
		final int bx1 = px1 >> BIN_AXIS_SHIFT;
		final int by0 = py0 >> BIN_AXIS_SHIFT;
		final int by1 = py1 >> BIN_AXIS_SHIFT;


		// Barycentric coordinates at bounding box origin
		int w0_row = wOrigin0;
		int w1_row = wOrigin1;
		int w2_row = wOrigin2;

		for (int by = by0; by <= by1; by++) {
			// Barycentric coordinates
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;

			for (int bx = bx0; bx <= bx1; bx++) {
				final int x0 = bx == bx0 ? (px0 & BIN_PIXEL_INDEX_MASK) : 0;
				final int y0 = by == by0 ? (py0 & BIN_PIXEL_INDEX_MASK) : 0;
				final int x1 = bx == bx1 ? (px1 & BIN_PIXEL_INDEX_MASK) : 7;
				final int y1 = by == by1 ? (py1 & BIN_PIXEL_INDEX_MASK) : 7;

				drawBin(bx, by, x0, y0, x1, y1, w0, w1, w2);

				// Step to the right
				if (bx == bx0) {
					final int xSteps = 8 - (px0 & BIN_PIXEL_INDEX_MASK);
					w0 += a0 * xSteps;
					w1 += a1 * xSteps;
					w2 += a2 * xSteps;
				} else {
					w0 += aLow0;
					w1 += aLow1;
					w2 += aLow2;
				}
			}

			// Row step
			if (by == by0) {
				final int ySteps = 8 - (py0 & BIN_PIXEL_INDEX_MASK);
				w0_row += b0 * ySteps;
				w1_row += b1 * ySteps;
				w2_row += b2 * ySteps;
			} else {
				w0_row += bLow0;
				w1_row += bLow1;
				w2_row += bLow2;
			}
		}
	}

	private void drawPixel(int x, int y) {
		lowBins[wordIndex(x, y)] |= (1L << (pixelIndex(x, y)));
	}

	@Override
	protected boolean testTri(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		if (!prepareTriBounds(v0, v1, v2)) {
			return false;
		}

		if (triNeedsClipped()) {
			return testClippedLowX(v0, v1, v2);
		}

		if (minPixelX == maxPixelX) {
			if(minPixelY == maxPixelY) {
				return testPixel(minPixelX, minPixelY);
			} else {
				for(int y = minPixelY; y <= maxPixelY; y++) {
					if (testPixel(minPixelX, y)) {
						return true;
					}
				}

				return false;
			}
		} else if (minPixelY == maxPixelY) {
			for(int x = minPixelX; x <= maxPixelX; x++) {
				if (testPixel(x, minPixelY)) {
					return true;
				}
			}

			return false;
		}

		final boolean result = testTriFast(v0, v1, v2);

		return result;
	}

	@SuppressWarnings("unused")
	private boolean testTriReference(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		prepareTriScan();

		// Triangle setup
		final int a0 = this.a0;
		final int b0 = this.b0;
		final int a1 = this.a1;
		final int b1 = this.b1;
		final int a2 = this.a2;
		final int b2 = this.b2;

		// Barycentric coordinates at bounding box origin
		int w0_row = wOrigin0;
		int w1_row = wOrigin1;
		int w2_row = wOrigin2;

		// Rasterize
		for (int y = minPixelY; y <= maxPixelY; y++) {
			// Barycentric coordinates
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;

			for (int x = minPixelX; x <= maxPixelX; x++) {
				// If p is on or inside all edges, render pixel.
				if ((w0 | w1 | w2) >= 0 && testPixel(x, y)) {
					return true;
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
			}

			// One row step
			w0_row += b0;
			w1_row += b1;
			w2_row += b2;
		}

		return false;
	}

	private boolean testTriFast(ProjectionVector4f v0, ProjectionVector4f v1, ProjectionVector4f v2) {
		prepareTriScan();

		final int px0 = minPixelX;
		final int py0 = minPixelY;
		final int px1 = maxPixelX;
		final int py1 = maxPixelY;

		final int bx0 = px0 >> BIN_AXIS_SHIFT;
		final int bx1 = px1 >> BIN_AXIS_SHIFT;
				final int by0 = py0 >> BIN_AXIS_SHIFT;
		final int by1 = py1 >> BIN_AXIS_SHIFT;

		// Barycentric coordinates at bounding box origin
		int w0_row = wOrigin0;
		int w1_row = wOrigin1;
		int w2_row = wOrigin2;

		for (int by = by0; by <= by1; by++) {
			// Barycentric coordinates
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;

			for (int bx = bx0; bx <= bx1; bx++) {
				final int x0 = bx == bx0 ? (px0 & BIN_PIXEL_INDEX_MASK) : 0;
				final int y0 = by == by0 ? (py0 & BIN_PIXEL_INDEX_MASK) : 0;
				final int x1 = bx == bx1 ? (px1 & BIN_PIXEL_INDEX_MASK) : 7;
				final int y1 = by == by1 ? (py1 & BIN_PIXEL_INDEX_MASK) : 7;

				if (testBin(bx, by, x0, y0, x1, y1, w0, w1, w2)) {
					return true;
				}

				// Step to the right
				if (bx == bx0) {
					final int xSteps = 8 - (px0 & BIN_PIXEL_INDEX_MASK);
					w0 += a0 * xSteps;
					w1 += a1 * xSteps;
					w2 += a2 * xSteps;
				} else {
					w0 += aLow0;
					w1 += aLow1;
					w2 += aLow2;
				}
			}

			// Row step
			if (by == by0) {
				final int ySteps = 8 - (py0 & BIN_PIXEL_INDEX_MASK);
				w0_row += b0 * ySteps;
				w1_row += b1 * ySteps;
				w2_row += b2 * ySteps;
			} else {
				w0_row += bLow0;
				w1_row += bLow1;
				w2_row += bLow2;
			}
		}

		return false;
	}

	/**
	 *
	 * @param binX bin x index
	 * @param binY bin y index
	 * @param px0 bin-relative min x, 0-7, inclusive
	 * @param py0 bin-relative min y, 0-7, inclusive
	 * @param px1 bin-relative max x, 0-7, inclusive
	 * @param py1 bin-relative max y, 0-7, inclusive
	 * @param wo0 edge weight 0 at x0, y0
	 * @param wo1 edge weight 1 at x0, y0
	 * @param wo2 edge weight 2 at x0, y0
	 * @return
	 */
	private boolean testBin(
			int binX,
			int binY,
			int px0,
			int py0,
			int px1,
			int py1,
			int w0_row,
			int w1_row,
			int w2_row)
	{

		final long word = lowBins[(binY << LOW_Y_SHIFT) | binX];

		if (word == -1L)
			// if bin fully occluded always false
			return false;
		else if ((px0 | py0) == 0 && (px1 & py1) == 7) {
			// testing whole bin

			// if whole bin is inside then any open pixel counts
			// and must have an open pixel if made it to here

			// if whole bin is outside then test must fail

			final int w0 = w0_row;
			final int w1 = w1_row;
			final int w2 = w2_row;

			int flags = (w0 | w1 | w2) >= 0 ? 1 : 0;
			if (((w0 + aLow0) | (w1 + aLow1) | (w2 + aLow2)) >= 0) flags |= 2;
			if (((w0 + bLow0) | (w1 + bLow1) | (w2 + bLow2)) >= 0) flags |= 4;
			if (((w0 + abLow0) | (w1 + abLow1) | (w2 + abLow2)) >= 0) flags |= 8;

			// PERF: need another way to handle corners and sub-bin tris/segments
			//			if(flags == 0) {
			//				// all corners outside
			//				return false;
			//			} else
			if (flags == 15) {
				// all corners inside and bin not fully occluded (per test at top)
				return true;
			}  else if (flags != 0 && word == 0) {
				// at least one corner inside and bin has no occlusion, must be true
				return true;
			}
		}


		// special case optimize for lines and points
		if (px0 == px1) {
			if(py0 == py1) {
				return (w0_row | w1_row | w2_row) >= 0 && testPixelInWordPreMasked(word, px0, py0);
			} else {
				int w0 = w0_row;
				int w1 = w1_row;
				int w2 = w2_row;
				final int b0 = this.b0;
				final int b1 = this.b1;
				final int b2 = this.b2;

				for(int y = py0; y <= py1; y++) {
					if ((w0 | w1 | w2) >= 0  && testPixelInWordPreMasked(word, px0, y)) {
						return true;
					}

					// One row step
					w0 += b0;
					w1 += b1;
					w2 += b2;
				}

				return false;
			}
		} else if (py0 == py1) {
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;
			final int a0 = this.a0;
			final int a1 = this.a1;
			final int a2 = this.a2;

			for(int x = px0; x <= px1; x++) {
				if ((w0 | w1 | w2) >= 0  && testPixelInWordPreMasked(word, x, py0)) {
					return true;
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
			}

			return false;
		}  else {
			final int a0 = this.a0;
			final int b0 = this.b0;
			final int a1 = this.a1;
			final int b1 = this.b1;
			final int a2 = this.a2;
			final int b2 = this.b2;

			// Rasterize
			for (int y = py0; y <= py1; y++) {
				int w0 = w0_row;
				int w1 = w1_row;
				int w2 = w2_row;

				for (int x = px0; x <= px1; x++) {
					// If p is on or inside all edges, render pixel.
					if ((w0 | w1 | w2) >= 0 && testPixelInWordPreMasked(word, x, y)) {
						return true;
					}

					// One step to the right
					w0 += a0;
					w1 += a1;
					w2 += a2;
				}

				// One row step
				w0_row += b0;
				w1_row += b1;
				w2_row += b2;
			}

			return false;
		}
	}

	/**
	 *
	 * @param binX bin x index
	 * @param binY bin y index
	 * @param px0 bin-relative min x, 0-7, inclusive
	 * @param py0 bin-relative min y, 0-7, inclusive
	 * @param px1 bin-relative max x, 0-7, inclusive
	 * @param py1 bin-relative max y, 0-7, inclusive
	 * @param wo0 edge weight 0 at x0, y0
	 * @param wo1 edge weight 1 at x0, y0
	 * @param wo2 edge weight 2 at x0, y0
	 * @return
	 */
	private void drawBin(
			int binX,
			int binY,
			int px0,
			int py0,
			int px1,
			int py1,
			int w0_row,
			int w1_row,
			int w2_row)
	{
		final int index = (binY << LOW_Y_SHIFT) | binX;
		long word = lowBins[index];

		if (word == -1L) {
			// if bin fully occluded nothing to do
			return;
		}

		// special case optimize for lines and points
		if (px0 == px1) {
			if(py0 == py1) {
				//				if (w0_row > 0 && w1_row > 0 && w2_row > 0)  {
				if ((w0_row | w1_row | w2_row) >= 0) {
					lowBins[index] = setPixelInWordPreMasked(word, px0, py0);
				}

				return;

			} else {
				int w0 = w0_row;
				int w1 = w1_row;
				int w2 = w2_row;
				final int b0 = this.b0;
				final int b1 = this.b1;
				final int b2 = this.b2;

				for(int y = py0; y <= py1; y++) {
					//					if (w0 > 0 && w1 > 0 && w2 > 0) {
					if ((w0 | w1 | w2) >= 0) {
						word = setPixelInWordPreMasked(word, px0, y);
					}

					// One row step
					w0 += b0;
					w1 += b1;
					w2 += b2;
				}

				lowBins[index] = word;
				return;
			}
		} else if (py0 == py1) {
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;
			final int a0 = this.a0;
			final int a1 = this.a1;
			final int a2 = this.a2;

			for(int x = px0; x <= px1; x++) {
				//				if (w0 > 0 && w1 > 0 && w2 > 0) {
				if ((w0 | w1 | w2) >= 0) {
					word = setPixelInWordPreMasked(word, x, py0);
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
			}

			lowBins[index] = word;
			return;

		} else if ((px0 | py0) == 0 && (px1 & py1) == 7) {
			// if filling whole bin then do it quick
			final int w0 = w0_row;
			final int w1 = w1_row;
			final int w2 = w2_row;

			//			if (w0 > 0 && w1 > 0 && w2 > 0
			//					&& w0 + xBinStep0 > 0 && w1 + xBinStep1 > 0 && w2 + xBinStep2 > 0
			//					&& w0 + yBinStep0 > 0 && w1 + yBinStep1 > 0 && w2 + yBinStep2 > 0
			//					&& w0 + xyBinStep0 > 0 && w1 + xyBinStep1 > 0 && w2 + xyBinStep2 > 0) {

			if ((w0 | w1 | w2
					| (w0 + aLow0) | (w1 + aLow1) | (w2 + aLow2)
					| (w0 + bLow0) | (w1 + bLow1) | (w2 + bLow2)
					| (w0 + abLow0) | (w1 + abLow1) | (w2 + abLow2)) >= 0) {
				lowBins[index] = -1;
				return;
			}
		}

		final int a0 = this.a0;
		final int b0 = this.b0;
		final int a1 = this.a1;
		final int b1 = this.b1;
		final int a2 = this.a2;
		final int b2 = this.b2;

		// Rasterize
		for (int y = py0; y <= py1; y++) {
			int w0 = w0_row;
			int w1 = w1_row;
			int w2 = w2_row;

			for (int x = px0; x <= px1; x++) {
				// If p is on or inside all edges, render pixel.

				//				if (w0 > 0 && w1 > 0 && w2 > 0) {
				if ((w0 | w1 | w2) >= 0) {
					word = setPixelInWordPreMasked(word, x, y);
				}

				// One step to the right
				w0 += a0;
				w1 += a1;
				w2 += a2;
			}

			// One row step
			w0_row += b0;
			w1_row += b1;
			w2_row += b2;
		}

		lowBins[index] = word;
		return;
	}

	private static int wordIndex(int x, int y)  {
		return  ((y & BIN_PIXEL_INVERSE_MASK) << HEIGHT_WORD_RELATIVE_SHIFT) | (x >> BIN_AXIS_SHIFT);
	}

	private static int pixelIndex(int x, int y)  {
		return  ((y & BIN_PIXEL_INDEX_MASK) << BIN_AXIS_SHIFT) | (x & BIN_PIXEL_INDEX_MASK);
	}

	/** REQUIRES 0-7 inputs! */
	private static boolean testPixelInWordPreMasked(long word, int x, int y) {
		return (word & (1L << ((y << BIN_AXIS_SHIFT) | x))) == 0;
	}

	private static long setPixelInWordPreMasked(long word, int x, int y) {
		return word | (1L << ((y << BIN_AXIS_SHIFT) | x));
	}

	private boolean testPixel(int x, int y) {
		return (lowBins[wordIndex(x, y)] & (1L << (pixelIndex(x, y)))) == 0;
	}

	private long nextTime;
	private static final boolean DISABLE_RASTER_OUTPUT = !Configurator.debugOcclusionRaster;

	public void outputRaster() {
		if (DISABLE_RASTER_OUTPUT) {
			return;
		}

		final long t = System.currentTimeMillis();

		if (t >= nextTime) {
			nextTime = t + 1000;

			final NativeImage nativeImage = new NativeImage(PIXEL_WIDTH, PIXEL_HEIGHT, false);

			for (int x = 0; x < PIXEL_WIDTH; x++) {
				for (int y = 0; y < PIXEL_HEIGHT; y++) {
					nativeImage.setPixelRgba(x, y, testPixel(x, y) ? -1 :0xFF000000);
				}
			}

			nativeImage.mirrorVertically();

			final File file = new File(MinecraftClient.getInstance().runDirectory, "canvas_occlusion_raster.png");

			ResourceImpl.RESOURCE_IO_EXECUTOR.execute(() -> {
				try {
					nativeImage.writeFile(file);
				} catch (final Exception e) {
					CanvasMod.LOG.warn("Couldn't save occluder image", e);
				} finally {
					nativeImage.close();
				}

			});
		}
	}
}