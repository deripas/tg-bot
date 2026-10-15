package com.github.deripas.image;

import boofcv.abst.fiducial.QrCodeDetector;
import boofcv.factory.fiducial.ConfigQrCode;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayU8;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * QR codes in images. The detector of BoofCV reads the large fiscal code in a photo of a receipt,
 * where the detectors of OpenCV and ZXing fail on its small and blurred modules.
 */
@UtilityClass
public class QrCodes {

    /**
     * @return texts of the codes found in the image, empty if there are none
     */
    public static List<String> decode(ImageData image) {
        final GrayU8 gray = ConvertBufferedImage.convertFrom(image.toImage(), (GrayU8) null);
        // the detector keeps the state of the last image, so it is not shared between threads
        final QrCodeDetector<GrayU8> detector = FactoryFiducial.qrcode(new ConfigQrCode(), GrayU8.class);
        detector.process(gray);
        return detector.getDetections().stream()
                .map(code -> code.message)
                .toList();
    }
}
