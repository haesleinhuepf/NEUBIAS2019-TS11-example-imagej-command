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
import clearcl.ClearCLBuffer;
import coremem.enums.NativeTypeEnum;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.kernels.Kernels;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.legacy.LegacyService;
import net.imagej.mesh.Mesh;
import net.imagej.ops.OpService;
import net.imagej.patcher.LegacyInjector;
import net.imagej.table.*;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.Regions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        CLIJ clij = CLIJ.getInstance();
        ClearCLBuffer input = clij.push(inputImage);

        // blur the image a bit
        ClearCLBuffer blurred = clij.createCLBuffer(input.getDimensions(), NativeTypeEnum.Float);
        Kernels.blurFast(clij, input, blurred, 1, 1, 0);
        ClearCLBuffer background = clij.createCLBuffer(input.getDimensions(), NativeTypeEnum.Float);
        Kernels.blurFast(clij, input, background, 3, 3, 0);

        // subtract background; result becomes a Difference-of-Gaussian image
        ClearCLBuffer backgroundSubtracted = clij.createCLBuffer(input.getDimensions(), NativeTypeEnum.Float);
        Kernels.addImagesWeighted(clij, blurred, background, backgroundSubtracted, 1f, -1f);
        clij.show(backgroundSubtracted, "background subtracted");

        // threshold the image
        ClearCLBuffer thresholded = clij.createCLBuffer(input);
        Kernels.threshold(clij, backgroundSubtracted, thresholded, 1f);

        // erode ROI
        ClearCLBuffer eroded = clij.createCLBuffer(thresholded);
        Kernels.erodeBox(clij, thresholded, eroded);
        clij.show(eroded, "eroded");

        ImagePlus erodedImp = clij.pull(eroded);

        // cleanup GPU
        input.close();
        blurred.close();
        background.close();
        backgroundSubtracted.close();
        thresholded.close();
        eroded.close();

        Img<FloatType> erodedII = ImageJFunctions.convertFloat(erodedImp);

        // apply connected components labelling
        RandomAccessibleInterval rai2 = ij.op().convert().int32(erodedII);
        ImgLabeling cca = ij.op().labeling().cca(rai2, ConnectedComponents.StructuringElement.FOUR_CONNECTED);

        // measure the size of the labels and write them in a table
        IntColumn indexColumn = new IntColumn();
        FloatColumn areaColumn = new FloatColumn();
        FloatColumn averageColumn = new FloatColumn();

        Calibration calibration = inputImage.getCalibration();

        Img<ARGBType> result = ArrayImgs.argbs(new long[]{erodedII.dimension(0), erodedII.dimension(1), erodedII.dimension(2)});
        Random random = new Random();

        LabelRegions<IntegerType> regions = new LabelRegions(cca);
        int count = 0;
        for (LabelRegion region : regions) {
            Mesh mesh = ij.op().geom().marchingCubes(region);
            long pixelCount = region.size();

            DoubleType size = ij.op().geom().size(mesh);


            if (size.get() > 27 && size.get() < 343) {
                System.out.println("Region: " + region.size());

                indexColumn.add(count);
                areaColumn.add((float) (pixelCount * calibration.pixelWidth * calibration.pixelHeight));

                IterableInterval sample = Regions.sample(region, erodedII);
                RealType mean = ij.op().stats().mean(sample);
                averageColumn.add(mean.getRealFloat());

                ARGBType colour = new ARGBType(random.nextInt());

                IterableInterval<ARGBType> resultRegionIterable = Regions.sample(region, result);
                Cursor<ARGBType> cursor = resultRegionIterable.cursor();
                while (cursor.hasNext()) {
                    cursor.next().set(colour);
                }
            }
            count ++;
        }
        //ij.ui().show(result);
        ImageJFunctions.show(result);
        BdvFunctions.show(result, "Labelling");


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
