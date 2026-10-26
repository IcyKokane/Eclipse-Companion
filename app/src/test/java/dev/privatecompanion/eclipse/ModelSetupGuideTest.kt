package dev.privatecompanion.eclipse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSetupGuideTest {
    private val mib = 1024L * 1024

    @Test fun allowsKnownModelWithStagingReserve() {
        assertTrue(ModelSetupGuide.checkImport(1120 * mib, 1248 * mib).permitted)
    }

    @Test fun deniesKnownModelWithoutReserve() {
        assertFalse(ModelSetupGuide.checkImport(1120 * mib, 1248 * mib - 1).permitted)
    }

    @Test fun replacementStillRequiresFullExtraSpace() {
        assertFalse(ModelSetupGuide.checkImport(2 * 1024 * mib, 1500 * mib).permitted)
    }

    @Test fun unknownProviderCanStartWithMinimumSpace() {
        assertTrue(ModelSetupGuide.checkImport(-1, 384 * mib).permitted)
        assertFalse(ModelSetupGuide.checkImport(-1, 384 * mib - 1).permitted)
    }

    @Test fun unknownAvailableSpaceIsRejected() {
        assertFalse(ModelSetupGuide.checkImport(-1, -1).permitted)
    }

    @Test fun tinyAndOversizeFilesAreRejected() {
        assertFalse(ModelSetupGuide.checkImport(1023, 10_000 * mib).permitted)
        assertFalse(ModelSetupGuide.checkImport(ModelFiles.MAX_BYTES + 1, 10_000 * mib).permitted)
    }

    @Test fun producesReadableSizeLabels() {
        assertEquals("Unknown", ModelSetupGuide.sizeLabel(-1))
        assertEquals("112 MiB", ModelSetupGuide.sizeLabel(112 * mib))
        assertEquals("1.00 GiB", ModelSetupGuide.sizeLabel(1024 * mib))
    }

    @Test fun verifiedModelLinkPointsToSingleGGUF() {
        assertTrue(ModelSetupGuide.QWEN_DOWNLOAD_URL.startsWith("https://huggingface.co/Qwen/"))
        assertTrue(ModelSetupGuide.QWEN_DOWNLOAD_URL.endsWith("/qwen2.5-1.5b-instruct-q4_k_m.gguf"))
    }
}
