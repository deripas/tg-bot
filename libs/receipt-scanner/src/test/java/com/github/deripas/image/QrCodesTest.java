package com.github.deripas.image;

import com.github.deripas.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodesTest {

    @Test
    void codeIsDecoded() {
        final String link = "https://suf.purs.gov.rs/v/?vl=A1g4OUZYM0ZEQzM4RkRWTzBUfgAAVH4AABhIHQ%3D";

        assertThat(QrCodes.decode(ImageData.of(QrImages.of(link)))).containsExactly(link);
    }

    @Test
    void imageWithoutCodesGivesNone() {
        assertThat(QrCodes.decode(ImageData.of(new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB)))).isEmpty();
    }

    /**
     * The fiscal code of receipt 3: about 3 px per module in the photo of the whole receipt.
     */
    private static final String FISCAL_LINK = "https://suf.purs.gov.rs/v/?vl=A0haRktaNldFSFpGS1o2V0W3bQMA%2BUgDAJhsCAcAAAAAAAABoST%2FRngAAAAlXF11k8r4B9iqTtRp%2FA9nxXXEZGQdmRGmDPBndhyWABisP2jzFSPkcuse2ARDnmT8T3VW1FIhcAdqxxh9Zs0dvNvpMextlTntJsbs32Uk8SpBUCRVDbolhYX76JiyqyHoJus79sZlAOWaRX1%2BZorVUSIkob4z7jHKqT%2Fz%2BivdtbDbp2WL7TLPV2bfTqlhwB%2Fby7t2cJP4asV%2BgBJdktL6qB%2Btdx64hRaGQPVjVLbDMD%2FzjLZAfkdaz1O0XrVdDl4ndYPg6HJOcN85o8k%2F%2FdGmZaGEuDEMVKVYyiTXjnJUTzM6POxGlOhzTlnzGa0giFZz1W3%2FutKYcMf6uFJ2eCsJx8NmCmPdKusbaHTaXXhNNTYzT8AbplWMeh8gcobPf9%2FCCa%2BlQD5z0czNwemn8j0dHLzFI1Jt1oOYYmHrMQNrwF17yauaA%2Brco3qmpiz%2BRwXxsgpo6BXxeHrM1ODHXHDNq%2BVNjQe4sxUaFM8DThGT5mI92yYHiO7xrqD7EPKMZwjcyo4qBSN22%2FkuE0yOoRcCxQLUF%2BziiPM%2BWzOaIXIZvU0E4GatCgJaikrnvoPgZbwp4PFY%2BQdf%2FBnszz9dUwzZdTT%2F8AZBV7DeYM2QTgsIP%2BQUhMLVaTNvLWQxQaODtu78RoMCIJEYgAPa%2F5Ky%2FM%2Br9xbSDz%2BoT%2FSKZfO66zzwRvG0sxVFZ%2Bcrvwp%2Bj568%2FmA%3D";

    @Test
    void fiscalAndShopCodesAreReadInPhotoOfWholeReceipt() {
        assertThat(QrCodes.decode(TestImages.readData("3/PXL_20261011_095819059.jpg")))
                .containsExactlyInAnyOrder(FISCAL_LINK, "010701HZFKZ6WEHZFKZ6WE000022469510102610:47:511340580");
    }

    @Test
    void fiscalCodeIsReadInCutOutOfPhoto() {
        assertThat(QrCodes.decode(TestImages.readData("4/PXL_20261011_095819059_qr.jpg"))).containsExactly(FISCAL_LINK);
    }

    @Test
    void fiscalCodeIsReadInScreenshot() {
        final List<String> codes = QrCodes.decode(TestImages.readData("4/PXL_20261011_095819059_qr.jpg"));

        assertThat(codes).hasSize(1);
        assertThat(URI.create(codes.getFirst().strip()))
                .hasScheme("https")
                .hasHost("suf.purs.gov.rs")
                .hasPath("/v/")
                .hasParameter("vl");
    }
}
