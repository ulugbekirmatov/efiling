package com.irs.mef.diagnostics;

import gov.irs.mef.inputcomposition.BinaryAttachment;
import gov.irs.mef.inputcomposition.PostmarkedSubmissionArchive;
import gov.irs.mef.inputcomposition.SubmissionArchive;
import gov.irs.mef.inputcomposition.SubmissionBuilder;
import gov.irs.mef.inputcomposition.SubmissionContainer;
import gov.irs.mef.inputcomposition.SubmissionManifest;
import gov.irs.mef.inputcomposition.SubmissionXML;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.GregorianCalendar;

/**
 * Offline. Rebuilds the exact SDK archive from a captured inspector-ring snapshot and writes the
 * zip bytes to target/archive-dump so the entries the IRS receives can be inspected with unzip/xxd.
 * Run: mvn test -Dtest=SubmissionArchiveBytesDumpTest -Dmef.archive.dump.snapshot=<submissionId>
 */
@EnabledIfSystemProperty(named = "mef.archive.dump.snapshot", matches = ".+")
class SubmissionArchiveBytesDumpTest {

    static {
        if (System.getProperty("A2A_TOOLKIT_HOME") == null) {
            System.setProperty("A2A_TOOLKIT_HOME",
                    Path.of(System.getProperty("user.dir"), "src/main/resources/mef_config").toAbsolutePath().toString());
        }
    }

    @Test
    void dumpArchiveBytes() throws Exception {
        String submissionId = System.getProperty("mef.archive.dump.snapshot");
        Path ring = Path.of("data/inspector-ring", submissionId);
        String returnXml = Files.readString(ring.resolve("return.xml"));
        String manifestXml = Files.readString(ring.resolve("manifest.xml"));

        SubmissionManifest manifest = new SubmissionManifest("manifest.xml", manifestXml);
        SubmissionXML xml = new SubmissionXML("Return.xml", returnXml);
        SubmissionArchive archive = SubmissionBuilder.createIRSSubmissionArchive(
                submissionId, manifest, xml, new BinaryAttachment[0]);
        PostmarkedSubmissionArchive postmarked =
                SubmissionBuilder.createPostmarkedSubmissionArchive(archive, new GregorianCalendar());
        SubmissionContainer container =
                SubmissionBuilder.createSubmissionContainer(new PostmarkedSubmissionArchive[] {postmarked});

        Path out = Path.of("target/archive-dump", submissionId);
        Files.createDirectories(out);
        Files.write(out.resolve("submission.zip"), archive.getBytes());
        Files.write(out.resolve("container.zip"), container.getBytes());
        System.out.println("ARCHIVE DUMP " + out.toAbsolutePath());
    }
}
