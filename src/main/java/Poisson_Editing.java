import graphcut.Terminal;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.NewImage;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * A tutorial demo for demonstrating Poisson editing.
 *
 * @author Yili Zhao
 */
public class Poisson_Editing implements PlugIn
{
	protected ImagePlus src;
	protected ImagePlus dst;
	protected ImagePlus mask;

	@Override
	public void run(final String args)
	{
		final int[] ids = WindowManager.getIDList();
		if (ids == null || ids.length < 3)
		{
			IJ.showMessage("You should have three images open.");
			return;
		}
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
		//ImagePlus image = IJ.openImage("http://cs2.swfc.edu.cn/~zyl/wp-content/uploads/2015/05/strawberry.jpg");
		//image.show();

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
}
