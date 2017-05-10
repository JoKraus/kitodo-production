/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package de.sub.goobi.export.dms;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.config.ConfigProjects;
import de.sub.goobi.export.download.ExportMetsWithoutHibernate;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.tasks.EmptyTask;
import de.sub.goobi.metadaten.MetadatenVerifizierungWithoutHibernate;
import de.sub.goobi.metadaten.copier.CopierData;
import de.sub.goobi.metadaten.copier.DataCopier;
import de.sub.goobi.persistence.apache.FolderInformation;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.exceptions.SwapException;
import org.kitodo.data.database.helper.enums.MetadataFormat;
import org.kitodo.data.database.persistence.apache.ProcessManager;
import org.kitodo.data.database.persistence.apache.ProcessObject;
import org.kitodo.data.database.persistence.apache.ProjectManager;
import org.kitodo.data.database.persistence.apache.ProjectObject;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.file.FileService;

import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.exceptions.WriteException;
import ugh.fileformats.excel.RDFFile;
import ugh.fileformats.mets.MetsModsImportExport;

public class AutomaticDmsExportWithoutHibernate extends ExportMetsWithoutHibernate {
    private static final Logger logger = LogManager.getLogger(AutomaticDmsExportWithoutHibernate.class);
    ConfigProjects cp;
    private boolean exportWithImages = true;
    private boolean exportFullText = true;
    private FolderInformation fi;
    private ProjectObject project;
    private final ServiceManager serviceManager = new ServiceManager();
    private final FileService fileService = serviceManager.getFileService();

    /**
     * The field task holds an optional task instance. Its progress and its
     * errors will be passed to the task manager screen (if available) for
     * visualisation.
     */
    private EmptyTask task;

    public static final String DIRECTORY_SUFFIX = "_tif";

    public AutomaticDmsExportWithoutHibernate() {
    }

    public AutomaticDmsExportWithoutHibernate(boolean exportImages) {
        this.exportWithImages = exportImages;
    }

    public void setExportFulltext(boolean exportFullText) {
        this.exportFullText = exportFullText;
    }

    /**
     * DMS-Export an eine gewünschte Stelle.
     *
     * @param process
     *            object
     */

    @Override
    public boolean startExport(ProcessObject process) throws DAOException, IOException, PreferencesException,
            WriteException, SwapException, TypeNotAllowedForParentException, InterruptedException {
        this.myPrefs = serviceManager.getRulesetService()
                .getPreferences(ProcessManager.getRuleset(process.getRulesetId()));
        ;

        this.project = ProjectManager.getProjectById(process.getProjectId());

        this.cp = new ConfigProjects(this.project.getTitle());
        String atsPpnBand = process.getTitle();

        /*
         * Dokument einlesen
         */
        Fileformat gdzfile;
        Fileformat newfile;
        try {
            this.fi = new FolderInformation(process.getId(), process.getTitle());
            URI metadataPath = this.fi.getMetadataFilePath();
            gdzfile = process.readMetadataFile(metadataPath, this.myPrefs);
            switch (MetadataFormat.findFileFormatsHelperByName(this.project.getFileFormatDmsExport())) {
                case METS:
                    newfile = new MetsModsImportExport(this.myPrefs);
                    break;
                case METS_AND_RDF:
                default:
                    newfile = new RDFFile(this.myPrefs);
                    break;
            }

            newfile.setDigitalDocument(gdzfile.getDigitalDocument());
            gdzfile = newfile;

        } catch (Exception e) {
            if (task != null) {
                task.setException(e);
            }
            Helper.setFehlerMeldung(Helper.getTranslation("exportError") + process.getTitle(), e);
            logger.error("Export abgebrochen, xml-LeseFehler", e);
            return false;
        }

        String rules = ConfigCore.getParameter("copyData.onExport");
        if (rules != null && !rules.equals("- keine Konfiguration gefunden -")) {
            try {
                new DataCopier(rules).process(new CopierData(newfile, process));
            } catch (ConfigurationException e) {
                if (task != null) {
                    task.setException(e);
                }
                Helper.setFehlerMeldung("dataCopier.syntaxError", e.getMessage());
                return false;
            } catch (RuntimeException e) {
                if (task != null) {
                    task.setException(e);
                }
                Helper.setFehlerMeldung("dataCopier.runtimeException", e.getMessage());
                return false;
            }
        }

        trimAllMetadata(gdzfile.getDigitalDocument().getLogicalDocStruct());

        /*
         * Metadaten validieren
         */

        if (ConfigCore.getBooleanParameter("useMetadatenvalidierung")) {
            MetadatenVerifizierungWithoutHibernate mv = new MetadatenVerifizierungWithoutHibernate();
            if (!mv.validate(gdzfile, this.myPrefs, process.getId(), process.getTitle())) {
                return false;

            }
        }

        /*
         * Speicherort vorbereiten und downloaden
         */
        URI zielVerzeichnis;
        URI benutzerHome;

        zielVerzeichnis = this.project.getDmsImportImagesPath();
        benutzerHome = zielVerzeichnis;

        /* ggf. noch einen Vorgangsordner anlegen */
        if (this.project.isDmsImportCreateProcessFolder()) {
            URI benutzerProcessHome = benutzerHome.resolve(File.separator + process.getTitle());
            zielVerzeichnis = benutzerHome;
            /* alte Import-Ordner löschen */
            if (!fileService.delete(benutzerHome)) {
                Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(),
                        "Import folder could not be cleared");
                return false;
            }
            /* alte Success-Ordner löschen */
            File successFile = new File(this.project.getDmsImportSuccessPath() + File.separator + process.getTitle());
            if (!fileService.delete(successFile.toURI())) {
                Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(),
                        "Success folder could not be cleared");
                return false;
            }
            /* alte Error-Ordner löschen */
            File errorfile = new File(this.project.getDmsImportErrorPath() + File.separator + process.getTitle());
            if (!fileService.delete(errorfile.toURI())) {
                Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(),
                        "Error folder could not be cleared");
                return false;
            }

            if (!fileService.fileExist(benutzerProcessHome)) {
                fileService.createDirectory(benutzerHome, process.getTitle());
            }
            if (task != null) {
                task.setProgress(1);
            }
        }

        /*
         * der eigentliche Download der Images
         */
        try {
            if (this.exportWithImages) {
                imageDownload(process, benutzerHome, atsPpnBand, DIRECTORY_SUFFIX);
                fulltextDownload(process, benutzerHome, atsPpnBand, DIRECTORY_SUFFIX);
            } else if (this.exportFullText) {
                fulltextDownload(process, benutzerHome, atsPpnBand, DIRECTORY_SUFFIX);
            }

            directoryDownload(process, zielVerzeichnis);
        } catch (Exception e) {
            if (task != null) {
                task.setException(e);
            }
            Helper.setFehlerMeldung("Export canceled, Process: " + process.getTitle(), e);
            return false;
        }

        /*
         * zum Schluss Datei an gewünschten Ort exportieren entweder direkt in
         * den Import-Ordner oder ins Benutzerhome anschliessend den
         * Import-Thread starten
         */
        if (this.project.isUseDmsImport()) {
            if (task != null) {
                task.setWorkDetail(atsPpnBand + ".xml");
            }
            if (MetadataFormat
                    .findFileFormatsHelperByName(this.project.getFileFormatDmsExport()) == MetadataFormat.METS) {
                /* Wenn METS, dann per writeMetsFile schreiben... */
                writeMetsFile(process, benutzerHome + File.separator + atsPpnBand + ".xml", gdzfile, false);
            } else {
                /* ...wenn nicht, nur ein Fileformat schreiben. */
                gdzfile.write(benutzerHome + File.separator + atsPpnBand + ".xml");
            }

            /* ggf. sollen im Export mets und rdf geschrieben werden */
            if (MetadataFormat.findFileFormatsHelperByName(
                    this.project.getFileFormatDmsExport()) == MetadataFormat.METS_AND_RDF) {
                writeMetsFile(process, benutzerHome + File.separator + atsPpnBand + ".mets.xml", gdzfile, false);
            }

            Helper.setMeldung(null, process.getTitle() + ": ", "DMS-Export started");

            if (!ConfigCore.getBooleanParameter("exportWithoutTimeLimit")) {
                /* Success-Ordner wieder löschen */
                if (this.project.isDmsImportCreateProcessFolder()) {
                    File successFile = new File(
                            this.project.getDmsImportSuccessPath() + File.separator + process.getTitle());
                    fileService.delete(successFile.toURI());
                }
            }
        }
        if (task != null) {
            task.setProgress(100);
        }
        return true;
    }

    /**
     * Run through all metadata and children of given docstruct to trim the
     * strings calls itself recursively.
     */
    private void trimAllMetadata(DocStruct inStruct) {
        /* trimm all metadata values */
        if (inStruct.getAllMetadata() != null) {
            for (Metadata md : inStruct.getAllMetadata()) {
                if (md.getValue() != null) {
                    md.setValue(md.getValue().trim());
                }
            }
        }

        /* run through all children of docstruct */
        if (inStruct.getAllChildren() != null) {
            for (DocStruct child : inStruct.getAllChildren()) {
                trimAllMetadata(child);
            }
        }
    }

    /**
     * Download full text.
     *
     * @param myProcess
     *            process object
     * @param userHome
     *            safe file
     * @param atsPpnBand
     *            String
     * @param ordnerEndung
     *            String
     */
    public void fulltextDownload(ProcessObject myProcess, URI userHome, String atsPpnBand, final String ordnerEndung)
            throws IOException, InterruptedException, SwapException, DAOException {

        // download sources
        URI sources = fi.getSourceDirectory();
        if (fileService.fileExist(sources) && fileService.getSubUris(sources).size() > 0) {
            URI destination = userHome.resolve(File.separator + atsPpnBand + "_src");
            if (!fileService.fileExist(destination)) {
                fileService.createDirectory(userHome, atsPpnBand + "_src");
            }
            ArrayList<URI> dateien = fileService.getSubUris(sources);
            for (int i = 0; i < dateien.size(); i++) {
                if (fileService.isFile(dateien.get(i))) {
                    URI meinZiel = destination.resolve(File.separator + fileService.getFileName(dateien.get(i)));
                    fileService.copyFile(dateien.get(i), meinZiel);
                }
            }
        }

        URI ocr = fi.getOcrDirectory();
        if (fileService.fileExist(ocr)) {
            ArrayList<URI> folder = fileService.getSubUris(ocr);
            for (URI dir : folder) {
                if (fileService.isDirectory(dir) && fileService.getSubUris(dir).size() > 0
                        && fileService.getFileName(dir).contains("_")) {
                    String suffix = fileService.getFileName(dir)
                            .substring(fileService.getFileName(dir).lastIndexOf("_"));
                    URI destination = userHome.resolve(File.separator + atsPpnBand + suffix);
                    if (!fileService.fileExist(destination)) {
                        fileService.createDirectory(userHome, atsPpnBand + suffix);
                    }
                    ArrayList<URI> files = fileService.getSubUris(dir);
                    for (int i = 0; i < files.size(); i++) {
                        if (fileService.isFile(files.get(i))) {
                            URI target = destination.resolve(File.separator + fileService.getFileName(files.get(i)));
                            fileService.copyFile(files.get(i), target);
                        }
                    }
                }
            }
        }
    }

    /**
     * Download image.
     *
     * @param myProcess
     *            process object
     * @param userHome
     *            safe file
     * @param atsPpnBand
     *            String
     * @param ordnerEndung
     *            String
     */
    public void imageDownload(ProcessObject myProcess, URI userHome, String atsPpnBand, final String ordnerEndung)
            throws IOException, InterruptedException, SwapException, DAOException {
        /*
         * den Ausgangspfad ermitteln
         */
        URI tifOrdner = this.fi.getImagesTifDirectory(true);

        /*
         * jetzt die Ausgangsordner in die Zielordner kopieren
         */
        if (fileService.fileExist(tifOrdner) && fileService.getSubUris(tifOrdner).size() > 0) {
            URI zielTif = userHome.resolve(File.separator).resolve(atsPpnBand).resolve(ordnerEndung);

            /* bei Agora-Import einfach den Ordner anlegen */
            if (this.project.isUseDmsImport()) {
                if (!fileService.fileExist(zielTif)) {
                    fileService.createDirectory(userHome, atsPpnBand + ordnerEndung);
                }
            } else {
                /*
                 * wenn kein Agora-Import, dann den Ordner mit
                 * Benutzerberechtigung neu anlegen
                 */
                User myUser = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                try {
                    fileService.createDirectoryForUser(zielTif, myUser.getLogin());
                } catch (Exception e) {
                    if (task != null) {
                        task.setException(e);
                    }
                    Helper.setFehlerMeldung("Export canceled, error", "could not create destination directory");
                    logger.error("could not create destination directory", e);
                }
            }

            /* jetzt den eigentlichen Kopiervorgang */

            ArrayList<URI> dateien = fileService.getSubUris(Helper.dataFilter, tifOrdner);
            for (int i = 0; i < dateien.size(); i++) {
                if (task != null) {
                    task.setWorkDetail(fileService.getFileName(dateien.get(i)));
                }
                if (fileService.isFile(dateien.get(i))) {
                    URI meinZiel = zielTif.resolve(File.separator + fileService.getFileName(dateien.get(i)));
                    fileService.copyFile(dateien.get(i), meinZiel);
                }
                if (task != null) {
                    task.setProgress((int) ((i + 1) * 98d / dateien.size() + 1));
                    if (task.isInterrupted()) {
                        throw new InterruptedException();
                    }
                }
            }
            if (task != null) {
                task.setWorkDetail(null);
            }
        }
    }

    /**
     * Starts copying all directories configured in kitodo_config.properties
     * parameter "processDirs" to export folder.
     *
     * @param myProcess
     *            the process object
     * @param zielVerzeichnis
     *            the destination directory
     */
    private void directoryDownload(ProcessObject myProcess, URI zielVerzeichnis) throws IOException {
        String[] processDirs = ConfigCore.getStringArrayParameter("processDirs");

        for (String processDir : processDirs) {
            String directoryTitle = processDir.replace("(processtitle)", myProcess.getTitle());

            URI srcDir = fi.getProcessDataDirectory().resolve(directoryTitle);
            URI dstDir = zielVerzeichnis.resolve(directoryTitle);

            if (fileService.isDirectory(srcDir)) {
                fileService.copyFile(srcDir, dstDir);
            }
        }
    }

    /**
     * The method setTask() can be used to pass in a task instance. If that is
     * passed in, the progress in it will be updated during processing and
     * occurring errors will be passed to it to be visible in the task manager
     * screen.
     *
     * @param task
     *            object to submit progress updates and errors to
     */
    public void setTask(EmptyTask task) {
        this.task = task;
    }
}
