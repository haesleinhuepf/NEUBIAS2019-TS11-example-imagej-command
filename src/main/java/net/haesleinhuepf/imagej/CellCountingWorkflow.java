/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package net.haesleinhuepf.imagej;

import bdv.util.BdvFunctions;
import bdv.util.BdvStackSource;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.legacy.LegacyService;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.table.*;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
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

        // convert and show the input image
        RandomAccessibleInterval rai = ImageJFunctions.convertFloat(inputImage);
        //BdvStackSource originalBdvView = BdvFunctions.show(rai, "original");
        //RealType mean1 = ij.op().stats().mean(Views.iterable(rai));
        //originalBdvView.setDisplayRange(mean1.getRealFloat() - 100, mean1.getRealFloat() + 100);

        // blur the image a bit
        RandomAccessibleInterval blurred = ij.op().filter().gauss(rai, 1, 1, 0);

        // subtract background; result becomes a Difference-of-Gaussian image
        RandomAccessibleInterval background = ij.op().filter().gauss(rai, 2, 2, 0);
        IterableInterval backgroundSubtracted = ij.op().math().subtract(Views.iterable(blurred), Views.iterable(background));
        RandomAccessibleInterval backgroundSubtractedRai = ij.op().convert().float32(backgroundSubtracted);

        ij.ui().show(backgroundSubtractedRai);

        // threshold the image
        IterableInterval ii = Views.iterable(backgroundSubtractedRai);
        IterableInterval otsuThresholded = ij.op().threshold().otsu(ii);
        ij.ui().show(otsuThresholded);

        // apply connected components labelling
        RandomAccessibleInterval rai2 = ij.op().convert().int32(otsuThresholded);
        ImgLabeling cca = ij.op().labeling().cca(rai2, ConnectedComponents.StructuringElement.FOUR_CONNECTED);

        // measure the size of the labels and write them in a table
        IntColumn indexColumn = new IntColumn();
        FloatColumn areaColumn = new FloatColumn();
        FloatColumn averageColumn = new FloatColumn();

        Calibration calibration = inputImage.getCalibration();
        

        LabelRegions<IntegerType> regions = new LabelRegions(cca);
        int count = 0;
        for (LabelRegion region : regions) {

            long pixelCount = region.size();
            if (pixelCount < 9) {
                System.out.println("Region: " + region.size());

                indexColumn.add(count);
                areaColumn.add((float) (pixelCount * calibration.pixelWidth * calibration.pixelHeight));

                IterableInterval sample = Regions.sample(region, rai);
                RealType mean = ij.op().stats().mean(sample);
                averageColumn.add(mean.getRealFloat());
            }
            count ++;
        }

        Table table = new DefaultGenericTable();
        table.add(indexColumn);
        table.add(areaColumn);
        table.add(averageColumn);
        table.setColumnHeader(0, "Index");
        table.setColumnHeader(1, "Area in " + calibration.getUnit());
        table.setColumnHeader(2, "Mean intensity");
        ij.ui().show(table);
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
        final File file = new File("src/main/resources/drosophila_florence116.tif");
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
