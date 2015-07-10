import java.util.HashMap;

import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.NewImage;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;

/**
 * A tutorial demo for demonstrating Poisson editing.
 *
 * @author Yili Zhao
 */
public class Poisson_Editing implements PlugIn
{
	private ImagePlus src;
	private ImagePlus dst;
	private ImagePlus mask;
	private ImagePlus tsrc;
	private ImagePlus tmask;
	private ImagePlus out;
	
	// hard coding offset coordinate
	private int ox = 100;
	private int oy = 60;
	
	// Matrix solver
	public MatrixSolver solver;
	public Thread blendingThread;

	@Override
	public void run(final String args)
	{
		final int[] ids = WindowManager.getIDList();
		if (ids == null || ids.length < 3)
		{
			IJ.showMessage("You should have three images open.");
			return;
		}
		
		final String[] titles = new String[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			titles[i] = (WindowManager.getImage(ids[i])).getTitle();
		}
		
		final GenericDialog gd = new GenericDialog("Poisson Editing");
		gd.addMessage("Image Selection:");
		gd.addChoice("source_image", titles, titles[0]);
		gd.addChoice("target_image", titles, titles[1]);
		gd.addChoice("mask_image", titles, titles[2]);
		
		gd.showDialog();
		
		if (gd.wasCanceled())
		{
			return;
		}
		
		src = WindowManager.getImage(ids[gd.getNextChoiceIndex()]);
		dst = WindowManager.getImage(ids[gd.getNextChoiceIndex()]);
		mask = WindowManager.getImage(ids[gd.getNextChoiceIndex()]);
		
		// source image's width and height should be equal to mask's width and height
		if (src.getWidth() != mask.getWidth() || src.getHeight() != mask.getHeight())
		{
			IJ.showMessage("Source image should be the same dimension as the mask image.");
			return;
		}
		
		// verify mask
		if (!verifyMask())
		{
			IJ.showMessage("Mask verify failed.");
			return;
		}
		
		//blendingThread = new Thread(blender);
		//blendingThread.start();
		
		out = PoissonCloning();
		out.show("Poisson Composite");
	}
	
	/**
	 * Mask verify
	 * 
	 * @return
	 */
	private boolean verifyMask()
	{
		final int maskWidth  = mask.getWidth();
		final int maskHeight = mask.getHeight();
		
		final int dw = dst.getWidth();
		final int dh = dst.getHeight();
		
		final byte[] pixels = (byte[])((ByteProcessor)mask.getProcessor()).getPixels();
		
		// Verify if source mask translate outside target image
		for (int y = 0; y < maskHeight; y++)
		{
			final int yw = y * maskWidth;
			for (int x = 0; x < maskWidth; x++)
			{
				final int idx = yw + x;
				final byte p = pixels[idx];
				
				if (p != 0 && ((x + ox) >= dw || (y + oy) >= dh))
				{
					return false;
				}
			}
		}
		
		// Verify no mask on the boundary
		final int hw = (maskHeight - 1) * maskWidth;
		for (int x = 0; x < maskWidth; x++)
		{
			if (pixels[x] != 0 || pixels[hw + x] != 0)
			{
				return false;
			}
		}
		
		for (int y = 0; y < maskHeight; y++)
		{
			if (pixels[y * maskWidth] != 0 || pixels[y * maskWidth + maskWidth - 1] != 0)
			{
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Compute image gradient
	 * 
	 * @param in
	 * @param w
	 * @param h
	 * @return
	 */
	/*
	private double[] computeGradient(final double[] in, int w, int h)
	{
		double[] gradient = new double[w * h];
		
		for (int y = 1; y < h-1; y++)
		{
			final int yw1 = (y - 1) * w;
			final int yw2 = y * w;
			final int yw3 = (y + 1) * w;
			
			for (int x = 1; x < w-1; x++)
			{
				gradient[yw2 + x] = in[yw2 + x] * 4.0 -in[yw2 + x - 1] - in[yw2 + x + 1] - in[yw1 + x] - in[yw3 + x];
			}
		}
		
		return gradient;
	}
	*/
	
	/*
	private void SplitRGB(ColorProcessor ip, double[] r, double[] g, double[] b)
	{
		final int[] pixels = (int[])ip.getPixels();
		int w = ip.getWidth();
		int h = ip.getHeight();
		for (int j = 0; j < h; j++)
		{
			final int hw = j * w;
		    for (int i = 0; i < w; i++)
		    {
		        int value = pixels[i + hw];
		        // value is a bit-packed RGB value
		        r[i + hw] = value & 0xff;
		        g[i + hw] = (value >> 8) & 0xff;
		        b[i + hw] = (value >> 16) & 0xff;
		    }
		}
	}
	*/
	
	public void nextIteration()
	{
		for (int i = 0; i < 100; i++)
		{
			solver.nextIteration();
		}	
	}
	
	class IterationBlender implements Runnable
	{
		public void run()
		{
			int iteration = 0;
			double error;
			do
			{
				error = solver.getError();
				iteration++;
				nextIteration();
			} while (error > 1.0);
			
			IJ.log("Did " + iteration + " iterations");
		}
	}
	
	/**
	 * Poisson Cloning
	 * 
	 * @return
	 */
	public ImagePlus PoissonCloning()
	{
		final int srcWidth  = src.getWidth();
		final int srcHeight = src.getHeight();
		
		final int dstWidth  = dst.getWidth();
		final int dstHeight = dst.getHeight();
		
		// target source and target mask should be the same dimension as the target image
		tsrc  = NewImage.createRGBImage("tsrc", dstWidth, dstHeight, 1, NewImage.FILL_BLACK);
		tmask = NewImage.createByteImage("tmask", dstWidth, dstHeight, 1, NewImage.FILL_BLACK);
		
		final int[] srcPixels = (int[])((ColorProcessor)src.getProcessor()).getPixels();
		final byte[] srcMaskPixels = (byte[])((ByteProcessor)mask.getProcessor()).getPixels();
		final int[] tsrcPixels = (int[])((ColorProcessor)tsrc.getProcessor()).getPixels();
		final byte[] tmaskPixels = (byte[])((ByteProcessor)tmask.getProcessor()).getPixels();
		
		// Paste source image and mask to target image and target mask
		for (int y = 0; y < srcHeight; y++)
		{
			final int hsw = y * srcWidth;
			final int htw = (y + oy) * dstWidth;
			
			for (int x = 0; x < srcWidth; x++)
			{
				tsrcPixels[htw + x + ox] = srcPixels[hsw + x];
				tmaskPixels[htw + x + ox] = srcMaskPixels[hsw + x];
			}
		}
		
		// Split Red, Green and Blue channels
		//final double[] tsrcR = new double[dstWidth * dstHeight];
		//final double[] tsrcG = new double[dstWidth * dstHeight];
		//final double[] tsrcB = new double[dstWidth * dstHeight];
		
		//SplitRGB((ColorProcessor)tsrc.getProcessor(), tsrcR, tsrcG, tsrcB);
		
		//final double[] dstR = new double[dstWidth * dstHeight];
		//final double[] dstG = new double[dstWidth * dstHeight];
		//final double[] dstB = new double[dstWidth * dstHeight];
		
		//SplitRGB((ColorProcessor)dst.getProcessor(), dstR, dstG, dstB);
		
		//final double[] gradientR = computeGradient(tsrcR, dstWidth, dstHeight);
		//final double[] gradientG = computeGradient(tsrcG, dstWidth, dstHeight);
		//final double[] gradientB = computeGradient(tsrcB, dstWidth, dstHeight);
		
		// Build mapping from (x,y) to matrix index
		int count = 0;
		HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
		for (int y = 1; y < dstHeight - 1; y++)
		{
			final int yw = y * dstWidth;
			
			for (int x = 1; x < dstWidth - 1; x++)
			{
				final int id = yw + x;
				if ((tmaskPixels[id] & 0xff) != 0)
				{
					map.put(id, count);
					count++;
				}
			}
		}
		
		IJ.log("There are " + count + " variables to be computed.");
		
		solver = new MatrixSolver(tsrc, tmask, dst, dstWidth, dstHeight, count, map);
		IterationBlender blender = new IterationBlender();
		
		IJ.log("Starting the iterative solver, please waiting...");
		
		blender.run();
		
		/*
		// Build the spare matrix
		//CRSMatrix A = CRSMatrix.zero(count, count, 5 * count);
		double[] b1 = new double[count];
		double[] b2 = new double[count];
		double[] b3 = new double[count];
		
		OpenMapRealMatrix A = new OpenMapRealMatrix(count, count);
		count = -1;
		
		for (int y = 1; y < dstHeight - 1; y++)
		{
			final int yw = y * dstWidth;
			final int yw1 = (y - 1) * dstWidth;
			final int yw2 = (y + 1) * dstWidth;
			
			for (int x = 1; x < dstWidth - 1; x++)
			{
				final int id = yw + x;
				if ((tmaskPixels[id] & 0xff) != 0)
				{
					count++;
					
					// Top
					if ((tmaskPixels[yw1 + x] & 0xff) != 0)
					{
						final int colIndex = map.get(yw1 + x);
						A.addToEntry(count, colIndex, -1.0);
						//A.set(count, colIndex, -1);
					}
					else
					{
						b1[count] += dstR[yw1 + x];
						b2[count] += dstG[yw1 + x];
						b3[count] += dstB[yw1 + x];
					}
					
					// Bottom
					if ((tmaskPixels[yw2 + x] & 0xff) != 0)
					{
						final int colIndex = map.get(yw2 + x);
						//A.set(count, colIndex, -1);
						A.addToEntry(count, colIndex, -1.0);
					}
					else
					{
						b1[count] += dstR[yw2 + x];
						b2[count] += dstG[yw2 + x];
						b3[count] += dstB[yw2 + x];
					}
					
					// Left
					if ((tmaskPixels[yw + x - 1] & 0xff) != 0)
					{
						final int colIndex = map.get(yw + x - 1);
						//A.set(count, colIndex, -1);
						A.addToEntry(count, colIndex, -1.0);
					}
					else
					{
						b1[count] += dstR[yw + x - 1];
						b2[count] += dstG[yw + x - 1];
						b3[count] += dstB[yw + x - 1];
					}
					
					// Right
					if ((tmaskPixels[yw + x + 1] & 0xff) != 0)
					{
						final int colIndex = map.get(yw + x + 1);
						//A.set(count, colIndex, -1);
						A.addToEntry(count, colIndex, -1.0);
					}
					else
					{
						b1[count] += dstR[yw + x + 1];
						b2[count] += dstG[yw + x + 1];
						b3[count] += dstB[yw + x + 1];
					}
					
					//A.set(count, count, 4.0);
					A.addToEntry(count, count, 4.0);
					
					// Construct the guidance field
					b1[count] += gradientR[yw + x];
					b2[count] += gradientG[yw + x];
					b3[count] += gradientB[yw + x];
				}
			}
		}
		
		count++;
		
		IJ.log("conut = " + count);
		
		//BasicVector bv1 = new BasicVector(b1);
		//BasicVector bv2 = new BasicVector(b2);
		//BasicVector bv3 = new BasicVector(b3);
		
		RealMatrix b = MatrixUtils.createRealMatrix(count, 3);
		b.setColumn(0, b1);
		b.setColumn(1, b2);
		b.setColumn(2, b3);
		
		//RealVector bv1 = MatrixUtils.createRealVector(b1);
		//RealVector bv2 = MatrixUtils.createRealVector(b2);
		//RealVector bv3 = MatrixUtils.createRealVector(b3);
		
		// Solve Ax = b
		//LinearSystemSolver solver = new ForwardBackSubstitutionSolver(A);
		//Vector x1 = solver.solve(bv1);
		//Vector x2 = solver.solve(bv2);
		//Vector x3 = solver.solve(bv3);
		
		IJ.log("Starting solve Ax = b");
		
		final DecompositionSolver solver = new SingularValueDecomposition(A).getSolver();
		final RealMatrix X = solver.solve(b);
		
		final double[] x1 = X.getColumn(0);
		final double[] x2 = X.getColumn(1);
		final double[] x3 = X.getColumn(2);
		
		*/
		
		IJ.log("Solve Ax = b completed");
		
		out = solver.updateImage();
		
		return out;
	}

	public void showAbout()
	{
		IJ.showMessage("PoissonEditingDemo", "a tutorial for demonstrating poisson editing");
	}

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ,
	 * loads an image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args
	 *            unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins
		// menu
		Class<?> clazz = Poisson_Editing.class;
		String url = clazz.getResource(
				"/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring(5, url.length()
				- clazz.getName().length() - 6);
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();

		// open the Clown sample
		ImagePlus image1 = IJ.openImage("http://cs2.swfc.edu.cn/~zyl/wp-content/uploads/2015/07/F16.png");
		ImagePlus image2 = IJ.openImage("http://cs2.swfc.edu.cn/~zyl/wp-content/uploads/2015/07/canyon.png");
		ImagePlus image3 = IJ.openImage("http://cs2.swfc.edu.cn/~zyl/wp-content/uploads/2015/07/F16Mask.png");
		
		image1.show();
		image2.show();
		image3.show();
		
		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
}
