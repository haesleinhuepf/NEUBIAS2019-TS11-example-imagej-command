/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package net.haesleinhuepf.imagej;

import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This example illustrates how to create an ImageJ {@link Command} plugin.
 * <p>
 * The code here is a simple Gaussian blur using ImageJ Ops.
 * </p>
 * <p>
 * You should replace the parameter fields with your own inputs and outputs,
 * and replace the {@link run} method implementation with your own logic.
 * </p>
 */
@Plugin(type = Command.class, menuPath = "Plugins>Gauss Filtering")
public class CellCountingWorkflow<T extends RealType<T>> implements Command {

    @Parameter
    ImagePlus inputImage;

    @Parameter
    ImageJ ij;

    @Override
    public void run() {

        System.out.println("Hello world!");
        System.out.println("Current image is: " + inputImage.getTitle());

        // blur the image a bit
        RandomAccessibleInterval rai = ImageJFunctions.convertFloat(inputImage);
        RandomAccessibleInterval blurred = ij.op().filter().gauss(rai, 5);
        ij.ui().show(blurred);

        // threshold the image

        // apply connected components labelling

        // measure the size of the labels and write them in a table

        // measure the intensity of the labels and write them in the same table


    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // ask the user for a file to open
        final File file = new File("src/main/resources/blobs.gif");
                //ij.ui().chooseFile(null, "open");

        if (file != null) {
            // load the dataset
            ImagePlus imp = IJ.openImage(file.getAbsolutePath());
            imp.show();

            // invoke the plugin
            ij.command().run(CellCountingWorkflow.class, true);
        }
    }

}
