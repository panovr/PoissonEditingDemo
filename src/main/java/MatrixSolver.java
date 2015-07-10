import java.util.HashMap;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;

/**
 * Iterative sparse matrix solver based on Chris Tralie code.
 * 
 * @author ylzhao
 *
 */
public class MatrixSolver
{
	ImagePlus tsrc;
	ImagePlus tmask;
	ImagePlus dst;
	int width;
	int height;
	HashMap<Integer, Integer> map;
	int N;		// Number of guess
	int[] D;	// Diagonal
	int[][] R;	//Off-Diagonal
	// NOTE: This R actually stores the negative of the real R matrix
	double[][] X;	// Guess
	double[][] b;	// Target of Ax = b
	
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
		        final int RGB = pixels[i + hw];
		        
		        r[i + hw] = (RGB & 0xFF0000) >> 16;
		        g[i + hw] = (RGB & 0xFF00) >> 8;
		        b[i + hw] = RGB & 0xFF;
		    }
		}
	}
	
	public MatrixSolver(final ImagePlus tsrc, final ImagePlus tmask, final ImagePlus dst,
			final int width, final int height, final int N, final HashMap<Integer, Integer> map)
	{
		this.tsrc = tsrc;
		this.tmask = tmask;
		this.dst = dst;
		this.width = width;
		this.height = height;
		this.map = map;
		this.N = N;
    	D = new int[N];
    	R = new int[N][4];
    	X = new double[N][3];	// For the 3 color channels
    	b = new double[N][3];
    	int[][] dP = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    	
    	final byte[] tmaskPixels = (byte[])((ByteProcessor)tmask.getProcessor()).getPixels();
    	
    	// Split Red, Green and Blue channels
		final double[] tsrcR = new double[width * height];
		final double[] tsrcG = new double[width * height];
		final double[] tsrcB = new double[width * height];
		
		SplitRGB((ColorProcessor)tsrc.getProcessor(), tsrcR, tsrcG, tsrcB);
		
		final double[] dstR = new double[width * height];
		final double[] dstG = new double[width * height];
		final double[] dstB = new double[width * height];
		
		SplitRGB((ColorProcessor)dst.getProcessor(), dstR, dstG, dstB);
    	
    	for (int y = 1; y < height - 1; y++)
		{	
			for (int x = 1; x < width - 1; x++)
			{
				final int id = y * width + x;
				
				if ((tmaskPixels[id] & 0xff) != 0)
				{
					final int i = map.get(id);
					int Np = 0;
					
		    		final double pValueR = tsrcR[y * width + x];
		    		final double pValueG = tsrcG[y * width + x];
		    		final double pValueB = tsrcB[y * width + x];
		    		
					for (int k = 0; k < dP.length; k++)
					{
						final int x2 = x + dP[k][0];
		    			final int y2 = y + dP[k][1];
		    			R[i][k] = -1;
		    			
		    			if (x2 < 1 || x2 >= width - 1 || y2 < 1 || y2 >= height-1)
		    			{
		    				continue;
		    			}
		    			
		    			Np++;
		    			
		    			final int value = tmaskPixels[y2 * width + x2] & 0xff;  
		    			if (value == 0)
		    			{ 	// It's a border pixel
		    				b[i][0] += dstR[y2 * width + x2];
		    				b[i][1] += dstG[y2 * width + x2];
		    				b[i][2] += dstB[y2 * width + x2];
		    			}
		    			else
		    			{
		    				R[i][k] = map.get(y2 * width + x2);
		    				
				    		final double qValueR = tsrcR[y2 * width + x2];
				    		final double qValueG = tsrcG[y2 * width + x2];
				    		final double qValueB = tsrcB[y2 * width + x2];
		    				
		    				b[i][0] += pValueR - qValueR;
		    				b[i][1] += pValueG - qValueG;
		    				b[i][2] += pValueB - qValueB;
		    			}
					}
					
					D[i] = Np;
				}
			}
		}
	}
	
	/**
	 * Jacobi iterate method
	 */
    public void nextIteration()
    {
    	final double[][] nextX = new double[N][3];
    	
    	for (int i = 0; i < N; i++)
    	{
    		for (int k = 0; k < 3; k++)
    		{
                nextX[i][k] = b[i][k];
            }
    		
    		for (int n = 0; n < 4; n++)
    		{
    			if (R[i][n] >= 0)
    			{
    				final int index = R[i][n];
    				
    				for (int k = 0; k < 3; k++)
    				{
                        nextX[i][k] += X[index][k];
                    }
    			}
    		}
    		
    		for (int k = 0; k < 3; k++)
    		{
                nextX[i][k] /= (double) D[i];
            }
    	}

    	for (int i = 0; i < N; i++)
    	{
            X[i] = nextX[i];
        }
    }
    
    /**
     * Evaluate the error
     * 
     * @return error
     */
    public double getError()
    {
    	double total = 0.0;
    	
    	for (int i = 0; i < N; i++)
    	{
    		double[] error = {b[i][0], b[i][1], b[i][2]};
    		
    		for (int n = 0; n < 4; n++)
    		{
    			if (R[i][n] >= 0)
    			{
    				final int index = R[i][n];
    				for (int k = 0; k < 3; k++)
    				{
    					error[k] += X[index][k];
    				}
    			}    			
    		}
    		
    		error[0] -= D[i] * X[i][0];
    		error[1] -= D[i] * X[i][1];
    		error[2] -= D[i] * X[i][2];
    		
    		total += (error[0] * error[0] + error[1] * error[1] + error[2] * error[2]);
    	}
    	
    	return Math.sqrt(total);
    }
    
    public ImagePlus updateImage()
    {
		ImagePlus out = dst.duplicate();
		final int[] outpixels = (int[])((ColorProcessor)out.getProcessor()).getPixels();
		final byte[] tmaskPixels = (byte[])((ByteProcessor)tmask.getProcessor()).getPixels();
		
		for (int y = 1; y < height - 1; y++)
		{
			final int yw = y * width;
			
			for (int x = 1; x < width - 1; x++)
			{
				final int id = yw + x;
				final int mv = tmaskPixels[id] & 0xFF;
				
				if (mv != 0)
				{
					final int i = map.get(id);
					int R = (int)Math.round(X[i][0]);
		    		int G = (int)Math.round(X[i][1]);
		    		int B = (int)Math.round(X[i][2]);
		    		if (R > 255) R = 255;
		    		if (R < 0) R = 0;
		    		if (G > 255) G = 255;
		    		if (G < 0) G = 0;
		    		if (B > 255) B = 255;
		    		if (B < 0) B = 0;
		    		int RGB = (R<<16) & 0xFF0000 | (G<<8) & 0xFF00 | B & 0xFF;
					outpixels[id] = RGB;
				}
			}
		}
		
		return out;
    }
}