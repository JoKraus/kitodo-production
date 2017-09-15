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

package de.sub.goobi.forms;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import de.sub.goobi.config.ConfigCore;
import de.sub.goobi.export.dms.ExportDms;
import de.sub.goobi.export.download.ExportMets;
import de.sub.goobi.export.download.ExportPdf;
import de.sub.goobi.export.download.Multipage;
import de.sub.goobi.export.download.TiffHeader;
import de.sub.goobi.helper.GoobiScript;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.Page;
import de.sub.goobi.helper.PropertyListObject;
import de.sub.goobi.helper.WebDav;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.goobi.production.cli.helper.WikiFieldHelper;
import org.goobi.production.export.ExportXmlLog;
import org.goobi.production.flow.helper.SearchResultGeneration;
import org.goobi.production.flow.statistics.StatisticsManager;
import org.goobi.production.flow.statistics.StatisticsRenderingElement;
import org.goobi.production.flow.statistics.enums.StatisticsMode;
import org.goobi.production.properties.IProperty;
import org.goobi.production.properties.ProcessProperty;
import org.goobi.production.properties.PropertyParser;
import org.goobi.production.properties.Type;
import org.jdom.transform.XSLTransformException;
import org.jfree.chart.plot.PlotOrientation;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.beans.Project;
import org.kitodo.data.database.beans.Property;
import org.kitodo.data.database.beans.Task;
import org.kitodo.data.database.beans.Template;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.UserGroup;
import org.kitodo.data.database.beans.Workpiece;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.helper.enums.TaskEditType;
import org.kitodo.data.database.helper.enums.TaskStatus;
import org.kitodo.data.exceptions.DataException;
import org.kitodo.dto.ProcessDTO;
import org.kitodo.enums.ObjectType;
import org.kitodo.services.ServiceManager;
import org.kitodo.services.file.FileService;

/**
 * ProzessverwaltungForm class.
 *
 * @author Wulf Riebensahm
 */

@Named("ProzessverwaltungForm")
@ViewScoped
public class ProzessverwaltungForm extends BasisForm {
    private static final long serialVersionUID = 2838270843176821134L;
    private static final Logger logger = LogManager.getLogger(ProzessverwaltungForm.class);
    private Process myProzess = new Process();
    private Task mySchritt = new Task();
    private StatisticsManager statisticsManager;
    private List<ProcessCounterObject> myAnzahlList;
    private HashMap<String, Integer> myAnzahlSummary;
    private Property myProzessEigenschaft;
    private Template myVorlage;
    private Property myVorlageEigenschaft;
    private User myBenutzer;
    private UserGroup myBenutzergruppe;
    private Workpiece myWerkstueck;
    private Property myWerkstueckEigenschaft;
    private String modusAnzeige = "aktuell";
    private String modusBearbeiten = "";
    private String kitodoScript;
    private HashMap<String, Boolean> anzeigeAnpassen;
    private String myNewProcessTitle;
    private String selectedXslt = "";
    private StatisticsRenderingElement myCurrentTable;
    private boolean showClosedProcesses = false;
    private boolean showArchivedProjects = false;
    private List<ProcessProperty> processPropertyList;
    private ProcessProperty processProperty;
    private Map<Integer, PropertyListObject> containers = new TreeMap<>();
    private Integer container;
    private String addToWikiField = "";
    private List<ProcessDTO> processDTOS = new ArrayList<>();
    private boolean showStatistics = false;
    private transient ServiceManager serviceManager = new ServiceManager();
    private transient FileService fileService = serviceManager.getFileService();
    private static String DONEDIRECTORYNAME = "fertig/";
    private int processId;
    private int taskId;

    /**
     * Constructor.
     */
    public ProzessverwaltungForm() {
        this.anzeigeAnpassen = new HashMap<>();
        this.anzeigeAnpassen.put("lockings", false);
        this.anzeigeAnpassen.put("swappedOut", false);
        this.anzeigeAnpassen.put("selectionBoxes", false);
        this.anzeigeAnpassen.put("processId", false);
        this.anzeigeAnpassen.put("batchId", false);
        this.sortierung = "titelAsc";
        /*
         * Vorgangsdatum generell anzeigen?
         */
        LoginForm login = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
        if (login != null) {
            if (login.getMyBenutzer() != null) {
                this.anzeigeAnpassen.put("processDate", login.getMyBenutzer().isConfigProductionDateShow());
            } else {
                this.anzeigeAnpassen.put("processDate", false);
            }
        }
        DONEDIRECTORYNAME = ConfigCore.getParameter("doneDirectoryName", "fertig/");
    }

    /**
     * needed for ExtendedSearch.
     *
     * @return always true
     */
    public boolean getInitialize() {
        return true;
    }

    /**
     * Create new process.
     */
    private void newProcess() {
        this.myProzess = new Process();
        this.myNewProcessTitle = "";
        this.modusBearbeiten = "prozess";
    }

    /**
     * New Process.
     *
     * @return page
     */
    public String NeuVorlage() {
        this.myProzess = new Process();
        this.myNewProcessTitle = "";
        this.myProzess.setTemplate(true);
        this.modusBearbeiten = "prozess";
        return "/pages/ProzessverwaltungBearbeiten";
    }

    /**
     * Edit process.
     *
     * @return page
     */
    public String editProcess() {
        Reload();
        return "/pages/ProzessverwaltungBearbeiten";
    }

    /**
     * Save.
     *
     * @return page or empty String
     */
    public String Speichern() {
        /*
         * wenn der Vorgangstitel geändert wurde, wird dieser geprüft und bei
         * erfolgreicher Prüfung an allen relevanten Stellen mitgeändert
         */
        if (this.myProzess != null && this.myProzess.getTitle() != null) {
            if (!this.myProzess.getTitle().equals(this.myNewProcessTitle) && this.myNewProcessTitle != null) {
                if (!renameAfterProcessTitleChanged()) {
                    return null;
                }
            }

            try {
                serviceManager.getProcessService().save(this.myProzess);
            } catch (DataException e) {
                Helper.setFehlerMeldung("fehlerNichtSpeicherbar", e.getMessage());
                logger.error(e);
            }
        } else {
            Helper.setFehlerMeldung("titleEmpty");
        }
        return null;
    }

    /**
     * Remove.
     *
     * @return page or empty String
     */
    public String Loeschen() {
        deleteMetadataDirectory();
        try {
            serviceManager.getProcessService().remove(this.myProzess);
        } catch (DataException e) {
            Helper.setFehlerMeldung("could not delete ", e);
            logger.error(e);
            return null;
        }
        if (this.modusAnzeige.equals("vorlagen")) {
            return FilterVorlagen();
        } else {
            return filterAlleStart();
        }
    }

    /**
     * Remove content.
     *
     * @return String
     */
    public String ContentLoeschen() {
        // deleteMetadataDirectory();
        try {
            URI ocr = fileService.getOcrDirectory(this.myProzess);
            if (fileService.fileExist(ocr)) {
                fileService.delete(ocr);
            }
            URI images = fileService.getImagesDirectory(this.myProzess);
            if (fileService.fileExist(images)) {
                fileService.delete(images);
            }
        } catch (Exception e) {
            Helper.setFehlerMeldung("Can not delete metadata directory", e);
        }

        Helper.setMeldung("Content deleted");
        return null;
    }

    private boolean renameAfterProcessTitleChanged() {
        String validateRegEx = ConfigCore.getParameter("validateProzessTitelRegex", "[\\w-]*");
        if (!this.myNewProcessTitle.matches(validateRegEx)) {
            this.modusBearbeiten = "prozess";
            Helper.setFehlerMeldung(Helper.getTranslation("UngueltigerTitelFuerVorgang"));
            return false;
        } else {
            // process properties
            for (Property processProperty : this.myProzess.getProperties()) {
                if (processProperty != null && processProperty.getValue() != null) {
                    if (processProperty.getValue().contains(this.myProzess.getTitle())) {
                        processProperty.setValue(processProperty.getValue()
                                .replaceAll(this.myProzess.getTitle(), this.myNewProcessTitle));
                    }
                }
            }
            // template properties
            for (Template template : this.myProzess.getTemplates()) {
                for (Property templateProperty : template.getProperties()) {
                    if (templateProperty.getValue().contains(this.myProzess.getTitle())) {
                        templateProperty.setValue(templateProperty.getValue()
                                .replaceAll(this.myProzess.getTitle(), this.myNewProcessTitle));
                    }
                }
            }
            // workpiece properties
            for (Workpiece workpiece : this.myProzess.getWorkpieces()) {
                for (Property workpieceProperty : workpiece.getProperties()) {
                    if (workpieceProperty.getValue().contains(this.myProzess.getTitle())) {
                        workpieceProperty.setValue(workpieceProperty.getValue()
                                .replaceAll(this.myProzess.getTitle(), this.myNewProcessTitle));
                    }
                }
            }

            try {
                {
                    // renaming image directories
                    URI imageDirectory = fileService.getImagesDirectory(myProzess);
                    if (fileService.isDirectory(imageDirectory)) {
                        ArrayList<URI> subDirs = fileService.getSubUris(imageDirectory);
                        for (URI imageDir : subDirs) {
                            if (fileService.isDirectory(imageDir)) {
                                fileService.renameFile(imageDir, fileService.getFileName(imageDir)
                                        .replace(myProzess.getTitle(), myNewProcessTitle));
                            }
                        }
                    }
                }
                {
                    // renaming ocr directories
                    URI ocrDirectory = fileService.getOcrDirectory(myProzess);
                    if (fileService.isDirectory(ocrDirectory)) {
                        ArrayList<URI> subDirs = fileService.getSubUris(ocrDirectory);
                        for (URI imageDir : subDirs) {
                            if (fileService.isDirectory(imageDir)) {
                                fileService.renameFile(imageDir,
                                        imageDir.toString().replace(myProzess.getTitle(), myNewProcessTitle));
                            }
                        }
                    }
                }
                {
                    // renaming defined directories
                    String[] processDirs = ConfigCore.getStringArrayParameter("processDirs");
                    for (String processDir : processDirs) {
                        //TODO: check it out
                        URI processDirAbsolute = serviceManager.getProcessService()
                                .getProcessDataDirectory(myProzess)
                                .resolve(processDir.replace("(processtitle)", myProzess.getTitle()));

                        File dir = new File(processDirAbsolute);
                        if (dir.isDirectory()) {
                            dir.renameTo(new File(
                                    dir.getAbsolutePath().replace(myProzess.getTitle(), myNewProcessTitle)));
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("could not rename folder", e);
            }

            this.myProzess.setTitle(this.myNewProcessTitle);

            if (!this.myProzess.isTemplate()) {
                /* Tiffwriter-Datei löschen */
                GoobiScript gs = new GoobiScript();
                ArrayList<Process> pro = new ArrayList<>();
                pro.add(this.myProzess);
                gs.deleteTiffHeaderFile(pro);
                gs.updateImagePath(pro);
            }
        }
        return true;
    }

    private void deleteMetadataDirectory() {
        for (Task task : this.myProzess.getTasks()) {
            this.mySchritt = task;
            deleteSymlinksFromUserHomes();
        }
        try {
            fileService.delete(serviceManager.getProcessService().getProcessDataDirectory(this.myProzess));
            URI ocrDirectory = fileService.getOcrDirectory(this.myProzess);
            if (fileService.fileExist(ocrDirectory)) {
                fileService.delete(ocrDirectory);
            }
        } catch (Exception e) {
            Helper.setFehlerMeldung("Can not delete metadata directory", e);
        }
    }

    /**
     * Filter current processes.
     *
     * @return page
     */
    public String FilterAktuelleProzesse() {
        this.statisticsManager = null;
        this.myAnzahlList = null;

        try {
            if (this.filter.equals("")) {
                filterProcessesWithoutFilter();
            } else {
                filterProcessesWithFilter();
            }

            this.page = new Page<>(0, this.processDTOS);
        } catch (DataException e) {
            Helper.setFehlerMeldung("ProzessverwaltungForm.FilterAktuelleProzesse", e);
            return null;
        }
        this.modusAnzeige = "aktuell";
        return "/pages/ProzessverwaltungAlle";
    }

    /**
     * Filter templates.
     *
     * @return page
     */
    public String FilterVorlagen() {
        this.statisticsManager = null;
        this.myAnzahlList = null;
        try {
            if (this.filter.equals("")) {
                filterTemplatesWithoutFilter();
            } else {
                filterTemplatesWithFilter();
            }
        } catch (DataException e) {
            logger.error(e);
        }

        this.page = new Page<>(0, this.processDTOS);
        this.modusAnzeige = "vorlagen";
        return "/pages/ProzessverwaltungAlle";
    }

    /**
     * This method initializes the process list without any filter whenever the bean
     * is constructed.
     */
    @PostConstruct
    public void initializeProcessList() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        String originLink = params.get("linkId");
        if (Objects.equals(originLink, "newProcess")) {
            NeuenVorgangAnlegen();
        } else if (Objects.equals(originLink, "templates")) {
            FilterVorlagen();
        } else if (Objects.equals(originLink, "allProcesses")) {
            FilterAktuelleProzesse();
        }
        setFilter("");
    }

    /**
     * New process insert.
     *
     * @return page
     */
    public String NeuenVorgangAnlegen() {
        FilterVorlagen();
        if (this.page.getTotalResults() == 1) {
            ProcessDTO single = (ProcessDTO) this.page.getListReload().get(0);
            ProzesskopieForm pkf = (ProzesskopieForm) Helper.getManagedBeanValue("#{ProzesskopieForm}");
            try {
                Process process = serviceManager.getProcessService().convertDtoToBean(single);
                if (pkf != null) {
                    pkf.setProzessVorlage(process);
                    return pkf.prepare(process.getId());
                }
                return "";
            } catch (DAOException e) {
                logger.error(e);
            }
        }
        return "/pages/ProzessverwaltungAlle";
    }

    /**
     * Anzeige der Sammelbände filtern.
     */
    public String filterAlleStart() {
        this.statisticsManager = null;
        this.myAnzahlList = null;
        /*
         * Filter für die Auflistung anwenden
         */
        try {
            if (this.filter.equals("")) {
                if (this.modusAnzeige.equals("vorlagen")) {
                    filterTemplatesWithoutFilter();
                } else {
                    filterProcessesWithoutFilter();
                }
            } else {
                if (this.modusAnzeige.equals("vorlagen")) {
                    filterTemplatesWithFilter();
                } else {
                    filterProcessesWithFilter();
                }
            }
            this.page = new Page<>(0, processDTOS);
        } catch (DataException e) {
            Helper.setFehlerMeldung("fehlerBeimEinlesen", e.getMessage());
            return null;
        } catch (NumberFormatException ne) {
            Helper.setFehlerMeldung("Falsche Suchparameter angegeben", ne.getMessage());
            return null;
        } catch (UnsupportedOperationException e) {
            logger.error(e);
        }

        return "/pages/ProzessverwaltungAlle";
    }

    private void filterProcessesWithFilter() throws DataException {
        BoolQueryBuilder query = serviceManager.getFilterService().queryBuilder(this.filter, ObjectType.PROCESS, false, false, false);
        if (!this.showClosedProcesses) {
            query.must(serviceManager.getProcessService().getQuerySortHelperStatus(false));
        }
        if (!this.showArchivedProjects) {
            query.must(serviceManager.getProcessService().getQueryProjectArchived(false));
        }
        processDTOS = serviceManager.getProcessService().findByQuery(query, sortList(), false);
    }

    private void filterProcessesWithoutFilter() throws DataException {
        if (!this.showClosedProcesses) {
            if (!this.showArchivedProjects) {
                processDTOS = serviceManager.getProcessService().findNotClosedAndNotArchivedProcesses(sortList());
            } else {
                processDTOS = serviceManager.getProcessService().findNotClosedProcesses(sortList());
            }
        } else {
            if (!this.showArchivedProjects) {
                processDTOS = serviceManager.getProcessService().findAllNotArchivedWithoutTemplates(sortList());
            } else {
                processDTOS = serviceManager.getProcessService().findAllWithoutTemplates(sortList());
            }
        }
    }

    private void filterTemplatesWithFilter() throws DataException {
        BoolQueryBuilder query = serviceManager.getFilterService().queryBuilder(this.filter, ObjectType.PROCESS, true, false, false);
        if (!this.showClosedProcesses) {
            query.must(serviceManager.getProcessService().getQuerySortHelperStatus(false));
        }
        if (!this.showArchivedProjects) {
            query.must(serviceManager.getProcessService().getQueryProjectArchived(false));
        }
        processDTOS = serviceManager.getProcessService().findByQuery(query, sortList(), false);
    }

    private void filterTemplatesWithoutFilter() throws DataException {
        if (!this.showClosedProcesses) {
            if (!this.showArchivedProjects) {
                processDTOS = serviceManager.getProcessService().findAllNotClosedAndNotArchivedTemplates(sortList());
            } else {
                processDTOS = serviceManager.getProcessService().findAllNotClosedTemplates(sortList());
            }
        } else {
            if (!this.showArchivedProjects) {
                processDTOS = serviceManager.getProcessService().findNotArchivedTemplates(sortList());
            } else {
                processDTOS = serviceManager.getProcessService().findAllTemplates(sortList());
            }
        }
    }

    private String sortList() {
        String sort = SortBuilders.fieldSort("title").order(SortOrder.ASC).toString();
        if (this.sortierung.equals("titelAsc")) {
            sort += "," + SortBuilders.fieldSort("title").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("titelDesc")) {
            sort += "," + SortBuilders.fieldSort("title").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("batchAsc")) {
            sort += ", " + SortBuilders.fieldSort("batches.id").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("batchDesc")) {
            sort += ", " + SortBuilders.fieldSort("batches.id").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("projektAsc")) {
            sort += ", " + SortBuilders.fieldSort("project").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("projektDesc")) {
            sort += ", " + SortBuilders.fieldSort("project").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("vorgangsdatumAsc")) {
            sort += "," + SortBuilders.fieldSort("creationDate").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("vorgangsdatumDesc")) {
            sort += "," + SortBuilders.fieldSort("creationDate").order(SortOrder.DESC).toString();
        }
        if (this.sortierung.equals("fortschrittAsc")) {
            sort += "," + SortBuilders.fieldSort("sortHelperStatus").order(SortOrder.ASC).toString();
        }
        if (this.sortierung.equals("fortschrittDesc")) {
            sort += "," + SortBuilders.fieldSort("sortHelperStatus").order(SortOrder.DESC).toString();
        }
        return sort;
    }

    /**
     * Remove process property.
     */
    public String ProzessEigenschaftLoeschen() {
        try {
            serviceManager.getProcessService().getPropertiesInitialized(myProzess).remove(myProzessEigenschaft);
            serviceManager.getProcessService().save(myProzess);
        } catch (DataException e) {
            Helper.setFehlerMeldung("fehlerNichtLoeschbar", e.getMessage());
            logger.error(e);
        }
        return null;
    }

    /**
     * Remove properties.
     */
    public String VorlageEigenschaftLoeschen() {
        try {
            myVorlage.getProperties().remove(myVorlageEigenschaft);
            serviceManager.getProcessService().save(myProzess);
        } catch (DataException e) {
            Helper.setFehlerMeldung("fehlerNichtLoeschbar", e.getMessage());
            logger.error(e);
        }
        return null;
    }

    /**
     * Remove workpiece properties.
     */
    public String WerkstueckEigenschaftLoeschen() {
        try {
            myWerkstueck.getProperties().remove(myWerkstueckEigenschaft);
            serviceManager.getProcessService().save(myProzess);
        } catch (DataException e) {
            Helper.setFehlerMeldung("fehlerNichtLoeschbar", e.getMessage());
            logger.error(e);
        }
        return null;
    }

    /**
     * New process property.
     */
    public String ProzessEigenschaftNeu() {
        myProzessEigenschaft = new Property();
        return null;
    }

    public String VorlageEigenschaftNeu() {
        myVorlageEigenschaft = new Property();
        return null;
    }

    public String WerkstueckEigenschaftNeu() {
        myWerkstueckEigenschaft = new Property();
        return null;
    }

    /**
     * Take process property.
     *
     * @return empty String
     */
    public String ProzessEigenschaftUebernehmen() {
        serviceManager.getProcessService().getPropertiesInitialized(myProzess).add(myProzessEigenschaft);
        myProzessEigenschaft.getProcesses().add(myProzess);
        Speichern();
        return null;
    }

    /**
     * Take template property.
     *
     * @return empty String
     */
    public String VorlageEigenschaftUebernehmen() {
        myVorlage.getProperties().add(myVorlageEigenschaft);
        myVorlageEigenschaft.getTemplates().add(myVorlage);
        Speichern();
        return null;
    }

    /**
     * Take workpiece property.
     *
     * @return empty String
     */
    public String WerkstueckEigenschaftUebernehmen() {
        myWerkstueck.getProperties().add(myWerkstueckEigenschaft);
        myWerkstueckEigenschaft.getWorkpieces().add(myWerkstueck);
        Speichern();
        return null;
    }

    /**
     * New task.
     */
    public String SchrittNeu() {
        this.mySchritt = new Task();
        this.modusBearbeiten = "schritt";
        return "/pages/inc_Prozessverwaltung/schritt";
    }

    /**
     * Take task.
     */
    public void SchrittUebernehmen() {
        this.mySchritt.setEditTypeEnum(TaskEditType.ADMIN);
        mySchritt.setProcessingTime(new Date());
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben != null) {
            mySchritt.setProcessingUser(ben);
        }
        this.myProzess.getTasks().add(this.mySchritt);
        this.mySchritt.setProcess(this.myProzess);
        Speichern();
    }

    /**
     * Remove task.
     *
     * @return page
     */
    public String SchrittLoeschen() {
        this.myProzess.getTasks().remove(this.mySchritt);
        Speichern();
        deleteSymlinksFromUserHomes();
        return "/pages/ProzessverwaltungBearbeiten";
    }

    private void deleteSymlinksFromUserHomes() {
        WebDav myDav = new WebDav();
        /* alle Benutzer */
        for (User b : this.mySchritt.getUsers()) {
            try {
                myDav.uploadFromHome(b, this.mySchritt.getProcess());
            } catch (RuntimeException e) {
                logger.error(e);
            }
        }
        /* alle Benutzergruppen mit ihren Benutzern */
        for (UserGroup bg : this.mySchritt.getUserGroups()) {
            for (User b : bg.getUsers()) {
                try {
                    myDav.uploadFromHome(b, this.mySchritt.getProcess());
                } catch (RuntimeException e) {
                    logger.error(e);
                }
            }
        }
    }

    /**
     * Remove User.
     *
     * @return empty String
     */
    public String deleteUser() {
        Integer userId = Integer.valueOf(Helper.getRequestParameter("ID"));
        try {
            User user = serviceManager.getUserService().getById(userId);
            this.mySchritt.getUsers().remove(user);
            Speichern();
            return null;
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error on reading database", e.getMessage());
            return null;
        }
    }

    /**
     * Remove UserGroup.
     *
     * @return empty String
     */
    public String deleteUserGroup() {
        Integer userGroupId = Integer.valueOf(Helper.getRequestParameter("ID"));
        try {
            UserGroup userGroup = serviceManager.getUserGroupService().getById(userGroupId);
            this.mySchritt.getUserGroups().remove(userGroup);
            Speichern();
            return null;
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error on reading database", e.getMessage());
            return null;
        }
    }

    /**
     * Add UserGroup.
     *
     * @return empty String
     */
    public String addUserGroup() {
        Integer userGroupId = Integer.valueOf(Helper.getRequestParameter("ID"));
        try {
            UserGroup userGroup = serviceManager.getUserGroupService().getById(userGroupId);
            for (UserGroup taskUserGroup : this.mySchritt.getUserGroups()) {
                if (taskUserGroup.equals(userGroup)) {
                    return null;
                }
            }
            this.mySchritt.getUserGroups().add(userGroup);
            Speichern();
            return null;
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error on reading database", e.getMessage());
            return null;
        }
    }

    /**
     * Add User.
     *
     * @return empty String
     */
    public String addUser() {
        Integer userId = Integer.valueOf(Helper.getRequestParameter("ID"));
        try {
            User user = serviceManager.getUserService().getById(userId);
            for (User taskUser : this.mySchritt.getUsers()) {
                if (taskUser.equals(user)) {
                    return null;
                }
            }
            this.mySchritt.getUsers().add(user);
            Speichern();
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error on reading database", e.getMessage());
            return null;
        }
        return null;
    }

    /**
     * New Vorlagen.
     */
    public String VorlageNeu() {
        this.myVorlage = new Template();
        this.myProzess.getTemplates().add(this.myVorlage);
        this.myVorlage.setProcess(this.myProzess);
        Speichern();
        return "/pages/inc_Prozessverwaltung/vorlage";
    }

    /**
     * Take Vorlagen.
     */
    public String VorlageUebernehmen() {
        this.myProzess.getTemplates().add(this.myVorlage);
        this.myVorlage.setProcess(this.myProzess);
        Speichern();
        return null;
    }

    /**
     * Remove Vorlagen.
     */
    public String VorlageLoeschen() {
        this.myProzess.getTemplates().remove(this.myVorlage);
        Speichern();
        return "/pages/ProzessverwaltungBearbeiten";
    }

    /**
     * New werkstücke.
     */
    public String WerkstueckNeu() {
        this.myWerkstueck = new Workpiece();
        this.myProzess.getWorkpieces().add(this.myWerkstueck);
        this.myWerkstueck.setProcess(this.myProzess);
        Speichern();
        return "/pages/ProzessverwaltungBearbeitenWerkstueck";
    }

    /**
     * Take werkstücke.
     */
    public String WerkstueckUebernehmen() {
        this.myProzess.getWorkpieces().add(this.myWerkstueck);
        this.myWerkstueck.setProcess(this.myProzess);
        Speichern();
        return null;
    }

    /**
     * Remove werkstücke.
     */
    public String WerkstueckLoeschen() {
        this.myProzess.getWorkpieces().remove(this.myWerkstueck);
        Speichern();
        return "/pages/ProzessverwaltungBearbeiten";
    }

    /**
     * Export METS.
     */
    public void ExportMets() {
        ExportMets export = new ExportMets();
        try {
            export.startExport(this.myProzess);
        } catch (Exception e) {
            Helper.setFehlerMeldung(
                    "An error occurred while trying to export METS file for: " + this.myProzess.getTitle(), e);
            logger.error("ExportMETS error", e);
        }
    }

    /**
     * Export PDF.
     */
    public void ExportPdf() {
        ExportPdf export = new ExportPdf();
        try {
            export.startExport(this.myProzess);
        } catch (Exception e) {
            Helper.setFehlerMeldung(
                    "An error occurred while trying to export PDF file for: " + this.myProzess.getTitle(), e);
            logger.error("ExportPDF error", e);
        }
    }

    /**
     * Export DMS.
     */
    public void ExportDMS() {
        ExportDms export = new ExportDms();
        try {
            export.startExport(this.myProzess);
        } catch (Exception e) {
            Helper.setFehlerMeldung("An error occurred while trying to export to DMS for: " + this.myProzess.getTitle(),
                    e);
            logger.error("exportDMS error", e);
        }
    }

    /**
     * Export DMS page.
     */
    @SuppressWarnings("unchecked")
    public void ExportDMSPage() {
        ExportDms export = new ExportDms();
        Boolean flagError = false;
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            try {
                Process process = serviceManager.getProcessService().convertDtoToBean(proz);
                export.startExport(process);
            } catch (Exception e) {
                // without this a new exception is thrown, if an exception
                // caught here doesn't have an errorMessage
                String errorMessage;

                if (e.getMessage() != null) {
                    errorMessage = e.getMessage();
                } else {
                    errorMessage = e.toString();
                }
                Helper.setFehlerMeldung("ExportErrorID" + proz.getId() + ":", errorMessage);
                logger.error(e);
                flagError = true;
            }
        }
        if (flagError) {
            Helper.setFehlerMeldung("ExportFinishedWithErrors");
        } else {
            Helper.setMeldung(null, "ExportFinished", "");
        }
    }

    /**
     * Export DMS selection.
     */
    @SuppressWarnings("unchecked")
    public void ExportDMSSelection() {
        ExportDms export = new ExportDms();
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            if (proz.isSelected()) {
                try {
                    export.startExport(serviceManager.getProcessService().convertDtoToBean(proz));
                } catch (Exception e) {
                    Helper.setFehlerMeldung("ExportError", e.getMessage());
                    logger.error(e);
                }
            }
        }
        Helper.setMeldung(null, "ExportFinished", "");
    }

    /**
     * Export DMS hits.
     */
    @SuppressWarnings("unchecked")
    public void ExportDMSHits() {
        ExportDms export = new ExportDms();
        for (Process proz : (List<Process>) this.page.getCompleteList()) {
            try {
                export.startExport(proz);
            } catch (Exception e) {
                Helper.setFehlerMeldung("ExportError", e.getMessage());
                logger.error(e);
            }
        }
        Helper.setMeldung(null, "ExportFinished", "");
    }

    /**
     * Upload all from home.
     *
     * @return empty String
     */
    public String UploadFromHomeAlle() {
        WebDav myDav = new WebDav();
        List<URI> folder = myDav.uploadAllFromHome(DONEDIRECTORYNAME);
        myDav.removeAllFromHome(folder, URI.create(DONEDIRECTORYNAME));
        Helper.setMeldung(null, "directoryRemovedAll", DONEDIRECTORYNAME);
        return null;
    }

    /**
     * Upload from home.
     *
     * @return empty String
     */
    public String UploadFromHome() {
        WebDav myDav = new WebDav();
        myDav.uploadFromHome(this.myProzess);
        Helper.setMeldung(null, "directoryRemoved", this.myProzess.getTitle());
        return null;
    }

    /**
     * Download to home.
     */
    public void DownloadToHome() {
        /*
         * zunächst prüfen, ob dieser Band gerade von einem anderen Nutzer in
         * Bearbeitung ist und in dessen Homeverzeichnis abgelegt wurde, ansonsten
         * Download
         */
        if (!serviceManager.getProcessService().isImageFolderInUse(this.myProzess)) {
            WebDav myDav = new WebDav();
            myDav.downloadToHome(this.myProzess, false);
        } else {
            Helper.setMeldung(null,
                    Helper.getTranslation("directory ") + " " + this.myProzess.getTitle() + " "
                            + Helper.getTranslation("isInUse"),
                    serviceManager.getUserService()
                            .getFullName(serviceManager.getProcessService().getImageFolderInUseUser(this.myProzess)));
            WebDav myDav = new WebDav();
            myDav.downloadToHome(this.myProzess, true);
        }
    }

    /**
     * Download to home page.
     */
    @SuppressWarnings("unchecked")
    public void DownloadToHomePage() {
        WebDav myDav = new WebDav();
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            download(myDav, proz);
        }
        Helper.setMeldung(null, "createdInUserHome", "");
    }

    /**
     * Download to home selection.
     */
    @SuppressWarnings("unchecked")
    public void DownloadToHomeSelection() {
        WebDav myDav = new WebDav();
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            if (proz.isSelected()) {
                download(myDav, proz);
            }
        }
        Helper.setMeldung(null, "createdInUserHomeAll", "");
    }

    private void download(WebDav webDav, ProcessDTO processDTO) {
        try {
            Process process = serviceManager.getProcessService().convertDtoToBean(processDTO);
            if (!serviceManager.getProcessService().isImageFolderInUse(processDTO)) {
                webDav.downloadToHome(process, false);
            } else {
                Helper.setMeldung(null,
                        Helper.getTranslation("directory ") + " " + processDTO.getTitle() + " "
                                + Helper.getTranslation("isInUse"),
                        serviceManager.getUserService()
                                .getFullName(serviceManager.getProcessService().getImageFolderInUseUser(process)));
                webDav.downloadToHome(process, true);
            }
        } catch (DAOException e) {
            logger.error(e);
        }
    }

    /**
     * Download to home hits.
     */
    @SuppressWarnings("unchecked")
    public void DownloadToHomeHits() {
        WebDav myDav = new WebDav();
        for (Process proz : (List<Process>) this.page.getCompleteList()) {
            if (!serviceManager.getProcessService().isImageFolderInUse(proz)) {
                myDav.downloadToHome(proz, false);
            } else {
                Helper.setMeldung(null,
                        Helper.getTranslation("directory ") + " " + proz.getTitle() + " "
                                + Helper.getTranslation("isInUse"),
                        serviceManager.getUserService()
                                .getFullName(serviceManager.getProcessService().getImageFolderInUseUser(proz)));
                myDav.downloadToHome(proz, true);
            }
        }
        Helper.setMeldung(null, "createdInUserHomeAll", "");
    }

    /**
     * Set up processing status page.
     */
    @SuppressWarnings("unchecked")
    public void BearbeitungsstatusHochsetzenPage() throws DAOException, DataException {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            stepStatusUp(proz.getId());
        }
    }

    /**
     * Set up processing status selection.
     */
    @SuppressWarnings("unchecked")
    public void BearbeitungsstatusHochsetzenSelection() throws DAOException, DataException {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            if (proz.isSelected()) {
                stepStatusUp(proz.getId());
            }
        }
    }

    /**
     * Set up processing status hits.
     */
    @SuppressWarnings("unchecked")
    public void BearbeitungsstatusHochsetzenHits() throws DAOException, DataException {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getCompleteList()) {
            stepStatusUp(proz.getId());
        }
    }

    private void stepStatusUp(int processId) throws DAOException, DataException {
        List<Task> taskList = serviceManager.getProcessService().getById(processId).getTasks();

        for (Task t : taskList) {
            if (!t.getProcessingStatus().equals(TaskStatus.DONE.getValue())) {
                t.setProcessingStatus(t.getProcessingStatus() + 1);
                t.setEditType(TaskEditType.ADMIN.getValue());
                if (t.getProcessingStatus().equals(TaskStatus.DONE.getValue())) {
                    serviceManager.getTaskService().close(t, true);
                } else {
                    User user = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                    if (user != null) {
                        t.setProcessingUser(user);
                        serviceManager.getTaskService().save(t);
                    }
                }
                break;
            }
        }
    }

    private void debug(String message, List<Task> bla) {
        if (!logger.isWarnEnabled()) {
            return;
        }
        for (Task s : bla) {
            logger.warn(message + " " + s.getTitle() + "   " + s.getOrdering());
        }
    }

    private void stepStatusDown(Process proz) throws DataException {
        List<Task> tempList = new ArrayList<>(proz.getTasks());
        debug("templist: ", tempList);

        Collections.reverse(tempList);
        debug("reverse: ", tempList);

        for (Task step : tempList) {
            if (proz.getTasks().get(0) != step && step.getProcessingStatusEnum() != TaskStatus.LOCKED) {
                step.setEditTypeEnum(TaskEditType.ADMIN);
                mySchritt.setProcessingTime(new Date());
                User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                if (ben != null) {
                    mySchritt.setProcessingUser(ben);
                }
                step = serviceManager.getTaskService().setProcessingStatusDown(step);
                break;
            }
        }
        serviceManager.getProcessService().save(proz);
    }

    /**
     * Set down processing status page.
     */
    @SuppressWarnings("unchecked")
    public void BearbeitungsstatusRuntersetzenPage() throws DAOException, DataException {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            stepStatusDown(serviceManager.getProcessService().convertDtoToBean(proz));
        }
    }

    /**
     * Set down processing status selection.
     */
    @SuppressWarnings("unchecked")
    public void BearbeitungsstatusRuntersetzenSelection() throws DAOException, DataException {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getListReload()) {
            if (proz.isSelected()) {
                stepStatusDown(serviceManager.getProcessService().convertDtoToBean(proz));
            }
        }
    }

    /**
     * Set down processing status hits.
     */
    @SuppressWarnings("unchecked")
    public void BearbeitungsstatusRuntersetzenHits() throws DAOException, DataException {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getCompleteList()) {
            stepStatusDown(serviceManager.getProcessService().convertDtoToBean(proz));
        }
    }

    /**
     * Task status up.
     */
    public void SchrittStatusUp() throws DAOException, DataException {
        if (this.mySchritt.getProcessingStatusEnum() != TaskStatus.DONE) {
            this.mySchritt = serviceManager.getTaskService().setProcessingStatusUp(this.mySchritt);
            this.mySchritt.setEditTypeEnum(TaskEditType.ADMIN);
            Task task = serviceManager.getTaskService().getById(this.mySchritt.getId());
            if (this.mySchritt.getProcessingStatusEnum() == TaskStatus.DONE) {
                serviceManager.getTaskService().close(task, true);
            } else {
                mySchritt.setProcessingTime(new Date());
                User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
                if (ben != null) {
                    mySchritt.setProcessingUser(ben);
                }
            }
        }
        Speichern();
        deleteSymlinksFromUserHomes();
    }

    /**
     * Task status down.
     *
     * @return empty String
     */
    public String SchrittStatusDown() {
        this.mySchritt.setEditTypeEnum(TaskEditType.ADMIN);
        mySchritt.setProcessingTime(new Date());
        User ben = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
        if (ben != null) {
            mySchritt.setProcessingUser(ben);
        }
        this.mySchritt = serviceManager.getTaskService().setProcessingStatusDown(this.mySchritt);
        Speichern();
        deleteSymlinksFromUserHomes();
        return null;
    }

    /**
     * Auswahl mittels Selectboxen.
     */
    @SuppressWarnings("unchecked")
    public void SelectionAll() {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getList()) {
            proz.setSelected(true);
        }
    }

    /**
     * Auswahl mittels Selectboxen.
     */
    @SuppressWarnings("unchecked")
    public void SelectionNone() {
        for (ProcessDTO proz : (List<ProcessDTO>) this.page.getList()) {
            proz.setSelected(false);
        }
    }

    /*
     * Getter und Setter
     */

    public Process getMyProzess() {
        return this.myProzess;
    }

    /**
     * Set my process.
     *
     * @param myProzess
     *            Process object
     */
    public void setMyProzess(Process myProzess) {
        this.myProzess = myProzess;
        this.myNewProcessTitle = myProzess.getTitle();
        loadProcessProperties();
    }

    public Property getMyProzessEigenschaft() {
        return this.myProzessEigenschaft;
    }

    public void setMyProzessEigenschaft(Property myProzessEigenschaft) {
        this.myProzessEigenschaft = myProzessEigenschaft;
    }

    public Task getMySchritt() {
        return this.mySchritt;
    }

    public void setMySchritt(Task mySchritt) {
        this.mySchritt = mySchritt;
    }

    public void setMySchrittReload(Task mySchritt) {
        this.mySchritt = mySchritt;
    }

    public Template getMyVorlage() {
        return this.myVorlage;
    }

    public void setMyVorlage(Template myVorlage) {
        this.myVorlage = myVorlage;
    }

    public void setMyVorlageReload(Template myVorlage) {
        this.myVorlage = myVorlage;
    }

    public Property getMyVorlageEigenschaft() {
        return this.myVorlageEigenschaft;
    }

    public void setMyVorlageEigenschaft(Property myVorlageEigenschaft) {
        this.myVorlageEigenschaft = myVorlageEigenschaft;
    }

    public Workpiece getMyWerkstueck() {
        return this.myWerkstueck;
    }

    public void setMyWerkstueck(Workpiece myWerkstueck) {
        this.myWerkstueck = myWerkstueck;
    }

    public void setMyWerkstueckReload(Workpiece myWerkstueck) {
        this.myWerkstueck = myWerkstueck;
    }

    public Property getMyWerkstueckEigenschaft() {
        return this.myWerkstueckEigenschaft;
    }

    public void setMyWerkstueckEigenschaft(Property myWerkstueckEigenschaft) {
        this.myWerkstueckEigenschaft = myWerkstueckEigenschaft;
    }

    public String getModusAnzeige() {
        return this.modusAnzeige;
    }

    public void setModusAnzeige(String modusAnzeige) {
        this.sortierung = "titelAsc";
        this.modusAnzeige = modusAnzeige;
    }

    public String getModusBearbeiten() {
        return this.modusBearbeiten;
    }

    public void setModusBearbeiten(String modusBearbeiten) {
        this.modusBearbeiten = modusBearbeiten;
    }

    /**
     * Set ordering up.
     *
     * @return String
     */
    public String reihenfolgeUp() {
        this.mySchritt.setOrdering(this.mySchritt.getOrdering() - 1);
        Speichern();
        return Reload();
    }

    /**
     * Set ordering down.
     *
     * @return String
     */
    public String reihenfolgeDown() {
        this.mySchritt.setOrdering(this.mySchritt.getOrdering() + 1);
        Speichern();
        return Reload();
    }

    /**
     * Reload.
     *
     * @return String
     */
    public String Reload() {
        if (this.mySchritt != null && this.mySchritt.getId() != null) {
            try {
                Helper.getHibernateSession().refresh(this.mySchritt);
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("could not refresh step with id " + this.mySchritt.getId(), e);
                }
            }
        }
        if (this.myProzess != null && this.myProzess.getId() != null) {
            try {
                Helper.getHibernateSession().refresh(this.myProzess);
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("could not refresh process with id " + this.myProzess.getId(), e);
                }
            }
        }
        return null;
    }

    public User getMyBenutzer() {
        return this.myBenutzer;
    }

    public void setMyBenutzer(User myBenutzer) {
        this.myBenutzer = myBenutzer;
    }

    public UserGroup getMyBenutzergruppe() {
        return this.myBenutzergruppe;
    }

    public void setMyBenutzergruppe(UserGroup myBenutzergruppe) {
        this.myBenutzergruppe = myBenutzergruppe;
    }

    /**
     * Get choice of project.
     *
     * @return Integer
     */
    public Integer getProjektAuswahl() {
        if (this.myProzess.getProject() != null) {
            return this.myProzess.getProject().getId();
        } else {
            return 0;
        }
    }

    /**
     * Set choice of project.
     *
     * @param inProjektAuswahl
     *            Integer
     */
    public void setProjektAuswahl(Integer inProjektAuswahl) {
        if (inProjektAuswahl != 0) {
            try {
                this.myProzess.setProject(serviceManager.getProjectService().getById(inProjektAuswahl));
            } catch (DAOException e) {
                Helper.setFehlerMeldung("Projekt kann nicht zugewiesen werden", "");
                logger.error(e);
            }
        }
    }

    /**
     * Get list of projects.
     *
     * @return list of SelectItem objects
     */
    public List<SelectItem> getProjektAuswahlListe() {
        List<SelectItem> myProjekte = new ArrayList<>();
        List<Project> temp = serviceManager.getProjectService().getByQuery("from Project ORDER BY title");
        for (Project proj : temp) {
            myProjekte.add(new SelectItem(proj.getId(), proj.getTitle(), null));
        }
        return myProjekte;
    }

    /**
     * Calculate metadata and images pages.
     */
    @SuppressWarnings("unchecked")
    public void CalcMetadataAndImagesPage() {
        CalcMetadataAndImages(this.page.getListReload());
    }

    /**
     * Calculate metadata and images selection.
     */
    @SuppressWarnings("unchecked")
    public void CalcMetadataAndImagesSelection() {
        ArrayList<ProcessDTO> selection = new ArrayList<>();
        for (ProcessDTO p : (List<ProcessDTO>) this.page.getListReload()) {
            if (p.isSelected()) {
                selection.add(p);
            }
        }
        CalcMetadataAndImages(selection);
    }

    /**
     * Calculate metadata and images hits.
     */
    @SuppressWarnings("unchecked")
    public void CalcMetadataAndImagesHits() {
        CalcMetadataAndImages(this.page.getCompleteList());
    }

    private void CalcMetadataAndImages(List<ProcessDTO> inListe) {

        this.myAnzahlList = new ArrayList<>();
        int allMetadata = 0;
        int allDocstructs = 0;
        int allImages = 0;

        int maxImages = 1;
        int maxDocstructs = 1;
        int maxMetadata = 1;

        int countOfProcessesWithImages = 0;
        int countOfProcessesWithMetadata = 0;
        int countOfProcessesWithDocstructs = 0;

        int averageImages = 0;
        int averageMetadata = 0;
        int averageDocstructs = 0;

        for (ProcessDTO proz : inListe) {
            int tempImg = proz.getSortHelperImages();
            int tempMetadata = proz.getSortHelperMetadata();
            int tempDocstructs = proz.getSortHelperDocstructs();

            ProcessCounterObject pco = new ProcessCounterObject(proz.getTitle(), tempMetadata, tempDocstructs, tempImg);
            this.myAnzahlList.add(pco);

            if (tempImg > maxImages) {
                maxImages = tempImg;
            }
            if (tempMetadata > maxMetadata) {
                maxMetadata = tempMetadata;
            }
            if (tempDocstructs > maxDocstructs) {
                maxDocstructs = tempDocstructs;
            }
            if (tempImg > 0) {
                countOfProcessesWithImages++;
            }
            if (tempMetadata > 0) {
                countOfProcessesWithMetadata++;
            }
            if (tempDocstructs > 0) {
                countOfProcessesWithDocstructs++;
            }

            /* Werte für die Gesamt- und Durchschnittsberechnung festhalten */
            allImages += tempImg;
            allMetadata += tempMetadata;
            allDocstructs += tempDocstructs;
        }

        /* die prozentualen Werte anhand der Maximumwerte ergänzen */
        for (ProcessCounterObject pco : this.myAnzahlList) {
            pco.setRelImages(pco.getImages() * 100 / maxImages);
            pco.setRelMetadata(pco.getMetadata() * 100 / maxMetadata);
            pco.setRelDocstructs(pco.getDocstructs() * 100 / maxDocstructs);
        }

        if (countOfProcessesWithImages > 0) {
            averageImages = allImages / countOfProcessesWithImages;
        }

        if (countOfProcessesWithMetadata > 0) {
            averageMetadata = allMetadata / countOfProcessesWithMetadata;
        }

        if (countOfProcessesWithDocstructs > 0) {
            averageDocstructs = allDocstructs / countOfProcessesWithDocstructs;
        }

        this.myAnzahlSummary = new HashMap<>();
        this.myAnzahlSummary.put("sumProcesses", this.myAnzahlList.size());
        this.myAnzahlSummary.put("sumMetadata", allMetadata);
        this.myAnzahlSummary.put("sumDocstructs", allDocstructs);
        this.myAnzahlSummary.put("sumImages", allImages);
        this.myAnzahlSummary.put("averageImages", averageImages);
        this.myAnzahlSummary.put("averageMetadata", averageMetadata);
        this.myAnzahlSummary.put("averageDocstructs", averageDocstructs);
    }

    public HashMap<String, Integer> getMyAnzahlSummary() {
        return this.myAnzahlSummary;
    }

    public List<ProcessCounterObject> getMyAnzahlList() {
        return this.myAnzahlList;
    }

    /**
     * Starte GoobiScript über alle Treffer.
     */
    @SuppressWarnings("unchecked")
    public void kitodoScriptHits() {
        GoobiScript gs = new GoobiScript();
        try {
            gs.execute(serviceManager.getProcessService().convertDtosToBeans(this.page.getCompleteList()), this.kitodoScript);
        } catch (DAOException | DataException e) {
            logger.error(e);
        }
    }

    /**
     * Starte GoobiScript über alle Treffer der Seite.
     */
    @SuppressWarnings("unchecked")
    public void kitodoScriptPage() {
        GoobiScript gs = new GoobiScript();
        try {
            gs.execute(serviceManager.getProcessService().convertDtosToBeans(this.page.getListReload()), this.kitodoScript);
        } catch (DAOException | DataException e) {
            logger.error(e);
        }
    }

    /**
     * Starte GoobiScript über alle selectierten Treffer.
     */
    @SuppressWarnings("unchecked")
    public void kitodoScriptSelection() {
        ArrayList<ProcessDTO> selection = new ArrayList<>();
        for (ProcessDTO p : (List<ProcessDTO>) this.page.getListReload()) {
            if (p.isSelected()) {
                selection.add(p);
            }
        }
        GoobiScript gs = new GoobiScript();
        try {
            gs.execute(serviceManager.getProcessService().convertDtosToBeans(selection), this.kitodoScript);
        } catch (DAOException | DataException e) {
            logger.error(e);
        }
    }

    /**
     * Statistische Auswertung.
     */
    public void setStatisticsStatusVolumes() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.STATUS_VOLUMES, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
        this.statisticsManager.calculate();
    }

    /**
     * Statistic UserGroups.
     */
    public void setStatisticsUserGroups() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.USERGROUPS, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
        this.statisticsManager.calculate();
    }

    /**
     * Statistic runtime Tasks.
     */
    public void setStatisticsRuntimeSteps() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.SIMPLE_RUNTIME_STEPS, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
    }

    public void setStatisticsProduction() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.PRODUCTION, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
    }

    public void setStatisticsStorage() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.STORAGE, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
    }

    public void setStatisticsCorrection() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.CORRECTIONS, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
    }

    public void setStatisticsTroughput() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.THROUGHPUT, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
    }

    /**
     * Project's statistics.
     */
    public void setStatisticsProject() {
        this.statisticsManager = new StatisticsManager(StatisticsMode.PROJECTS, this.processDTOS,
                FacesContext.getCurrentInstance().getViewRoot().getLocale());
        this.statisticsManager.calculate();
    }

    /**
     * is called via jsf at the end of building a chart in include file
     * Prozesse_Liste_Statistik.xhtml and resets the statistics so that with the
     * next reload a chart is not shown anymore.
     */
    public String getResetStatistic() {
        this.showStatistics = false;
        return null;
    }

    public String getMyDatasetHoehe() {
        int bla = this.page.getCompleteList().size() * 20;
        return String.valueOf(bla);
    }

    public int getMyDatasetHoeheInt() {
        return this.page.getCompleteList().size() * 20;
    }

    public NumberFormat getMyFormatter() {
        return new DecimalFormat("#,##0");
    }

    public PlotOrientation getMyOrientation() {
        return PlotOrientation.HORIZONTAL;
    }

    /*
     * Downloads
     */

    public void DownloadTiffHeader() throws IOException {
        TiffHeader tiff = new TiffHeader(this.myProzess);
        tiff.exportStart();
    }

    public void DownloadMultiTiff() throws IOException {
        Multipage mp = new Multipage();
        mp.startExport(this.myProzess);
    }

    public String getKitodoScript() {
        return this.kitodoScript;
    }

    /**
     * Setter for kitodoScript.
     *
     * @param kitodoScript
     *            the kitodoScript
     */
    public void setKitodoScript(String kitodoScript) {
        this.kitodoScript = kitodoScript;
    }

    public HashMap<String, Boolean> getAnzeigeAnpassen() {
        return this.anzeigeAnpassen;
    }

    public void setAnzeigeAnpassen(HashMap<String, Boolean> anzeigeAnpassen) {
        this.anzeigeAnpassen = anzeigeAnpassen;
    }

    public String getMyNewProcessTitle() {
        return this.myNewProcessTitle;
    }

    public void setMyNewProcessTitle(String myNewProcessTitle) {
        this.myNewProcessTitle = myNewProcessTitle;
    }

    public StatisticsManager getStatisticsManager() {
        return this.statisticsManager;
    }

    /**
     * Getter for showStatistics.
     *
     * @return the showStatistics
     */
    public boolean isShowStatistics() {
        return this.showStatistics;
    }

    /**
     * Setter for showStatistics.
     *
     * @param showStatistics
     *            the showStatistics to set
     */
    public void setShowStatistics(boolean showStatistics) {
        this.showStatistics = showStatistics;
    }

    public static class ProcessCounterObject {
        private String title;
        private int metadata;
        private int docstructs;
        private int images;
        private int relImages;
        private int relDocstructs;
        private int relMetadata;

        /**
         * Constructor.
         *
         * @param title
         *            String
         * @param metadata
         *            int
         * @param docstructs
         *            int
         * @param images
         *            int
         */
        public ProcessCounterObject(String title, int metadata, int docstructs, int images) {
            super();
            this.title = title;
            this.metadata = metadata;
            this.docstructs = docstructs;
            this.images = images;
        }

        public int getImages() {
            return this.images;
        }

        public int getMetadata() {
            return this.metadata;
        }

        public String getTitle() {
            return this.title;
        }

        public int getDocstructs() {
            return this.docstructs;
        }

        public int getRelDocstructs() {
            return this.relDocstructs;
        }

        public int getRelImages() {
            return this.relImages;
        }

        public int getRelMetadata() {
            return this.relMetadata;
        }

        public void setRelDocstructs(int relDocstructs) {
            this.relDocstructs = relDocstructs;
        }

        public void setRelImages(int relImages) {
            this.relImages = relImages;
        }

        public void setRelMetadata(int relMetadata) {
            this.relMetadata = relMetadata;
        }
    }

    /**
     * starts generation of xml logfile for current process.
     */

    public void CreateXML() {
        try {
            ExportXmlLog xmlExport = new ExportXmlLog();

            LoginForm login = (LoginForm) Helper.getManagedBeanValue("#{LoginForm}");
            String directory = new File(serviceManager.getUserService().getHomeDirectory(login.getMyBenutzer()))
                    .getPath();
            String destination = directory + this.myProzess.getTitle() + "_log.xml";
            xmlExport.startExport(this.myProzess, destination);
        } catch (IOException e) {
            Helper.setFehlerMeldung("could not  write logfile to home directory:", e);
        }
    }

    /**
     * transforms xml logfile with given xslt and provides download.
     */
    public void TransformXml() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {
            String OutputFileName = "export.xml";
            /*
             * Vorbereiten der Header-Informationen
             */
            HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();

            ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
            String contentType = servletContext.getMimeType(OutputFileName);
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment;filename=\"" + OutputFileName + "\"");

            response.setContentType("text/xml");

            try {
                ServletOutputStream out = response.getOutputStream();
                ExportXmlLog export = new ExportXmlLog();
                export.startTransformation(out, this.myProzess, this.selectedXslt);
                out.flush();
            } catch (IOException | XSLTransformException e) {
                Helper.setFehlerMeldung("Could not create transformation: ", e);
            }
            facesContext.responseComplete();
        }
    }

    /**
     * Get XSLT list.
     *
     * @return list of Strings
     */
    public List<URI> getXsltList() {
        List<URI> answer = new ArrayList<>();
        try {
            URI folder = fileService.createDirectory(null, "xsltFolder");
            if (fileService.isDirectory(folder) && fileService.fileExist(folder)) {
                ArrayList<URI> files = fileService.getSubUris(folder);

                for (URI uri : files) {
                    if (uri.toString().endsWith(".xslt") || uri.toString().endsWith(".xsl")) {
                        answer.add(uri);
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e);
        }
        return answer;
    }

    public void setSelectedXslt(String select) {
        this.selectedXslt = select;
    }

    public String getSelectedXslt() {
        return this.selectedXslt;
    }

    /**
     * Downloads a docket for myProcess.
     *
     * @return The navigation string
     */
    public String downloadDocket() throws IOException {
        serviceManager.getProcessService().downloadDocket(this.myProzess);
        return "";
    }

    public void setMyCurrentTable(StatisticsRenderingElement myCurrentTable) {
        this.myCurrentTable = myCurrentTable;
    }

    public StatisticsRenderingElement getMyCurrentTable() {
        return this.myCurrentTable;
    }

    /**
     * Create excel.
     */
    public void CreateExcel() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {

            /*
             * Vorbereiten der Header-Informationen
             */
            HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
            try {
                ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
                String contentType = servletContext.getMimeType("export.xls");
                response.setContentType(contentType);
                response.setHeader("Content-Disposition", "attachment;filename=\"export.xls\"");
                ServletOutputStream out = response.getOutputStream();
                HSSFWorkbook wb = (HSSFWorkbook) this.myCurrentTable.getExcelRenderer().getRendering();
                wb.write(out);
                out.flush();
                facesContext.responseComplete();

            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    /**
     * Generate result as PDF.
     */
    public void generateResultAsPdf() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {

            /*
             * Vorbereiten der Header-Informationen
             */
            HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
            try {
                ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
                String contentType = servletContext.getMimeType("search.pdf");
                response.setContentType(contentType);
                response.setHeader("Content-Disposition", "attachment;filename=\"search.pdf\"");
                ServletOutputStream out = response.getOutputStream();

                SearchResultGeneration sr = new SearchResultGeneration(this.filter, this.showClosedProcesses,
                        this.showArchivedProjects);
                HSSFWorkbook wb = sr.getResult();
                List<List<HSSFCell>> rowList = new ArrayList<>();
                HSSFSheet mySheet = wb.getSheetAt(0);
                Iterator<Row> rowIter = mySheet.rowIterator();
                while (rowIter.hasNext()) {
                    HSSFRow myRow = (HSSFRow) rowIter.next();
                    Iterator<Cell> cellIter = myRow.cellIterator();
                    List<HSSFCell> row = new ArrayList<>();
                    while (cellIter.hasNext()) {
                        HSSFCell myCell = (HSSFCell) cellIter.next();
                        row.add(myCell);
                    }
                    rowList.add(row);
                }
                Document document = new Document();
                Rectangle a4quer = new Rectangle(PageSize.A3.getHeight(), PageSize.A3.getWidth());
                PdfWriter.getInstance(document, out);
                document.setPageSize(a4quer);
                document.open();
                if (rowList.size() > 0) {
                    Paragraph p = new Paragraph(rowList.get(0).get(0).toString());
                    document.add(p);
                    PdfPTable table = new PdfPTable(9);
                    table.setSpacingBefore(20);
                    for (List<HSSFCell> row : rowList) {
                        for (HSSFCell hssfCell : row) {
                            // TODO aufhübschen und nicht toString() nutzen
                            String stringCellValue = hssfCell.toString();
                            table.addCell(stringCellValue);
                        }
                    }
                    document.add(table);
                }

                document.close();
                out.flush();
                facesContext.responseComplete();
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }

    /**
     * Generate result set.
     */
    public void generateResult() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        if (!facesContext.getResponseComplete()) {

            /*
             * Vorbereiten der Header-Informationen
             */
            HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
            try {
                ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
                String contentType = servletContext.getMimeType("search.xls");
                response.setContentType(contentType);
                response.setHeader("Content-Disposition", "attachment;filename=\"search.xls\"");
                ServletOutputStream out = response.getOutputStream();
                SearchResultGeneration sr = new SearchResultGeneration(this.filter, this.showClosedProcesses,
                        this.showArchivedProjects);
                HSSFWorkbook wb = sr.getResult();
                wb.write(out);
                out.flush();
                facesContext.responseComplete();
            } catch (IOException e) {
                logger.error(e);
            }
        }
    }

    public boolean isShowClosedProcesses() {
        return this.showClosedProcesses;
    }

    public void setShowClosedProcesses(boolean showClosedProcesses) {
        this.showClosedProcesses = showClosedProcesses;
    }

    public void setShowArchivedProjects(boolean showArchivedProjects) {
        this.showArchivedProjects = showArchivedProjects;
    }

    public boolean isShowArchivedProjects() {
        return this.showArchivedProjects;
    }

    /**
     * Get wiki field.
     *
     * @return values for wiki field
     */
    public String getWikiField() {
        return this.myProzess.getWikiField();
    }

    /**
     * sets new value for wiki field.
     *
     * @param inString
     *            String
     */
    public void setWikiField(String inString) {
        this.myProzess.setWikiField(inString);
    }

    public String getAddToWikiField() {
        return this.addToWikiField;
    }

    public void setAddToWikiField(String addToWikiField) {
        this.addToWikiField = addToWikiField;
    }

    /**
     * Add to wiki field.
     */
    public void addToWikiField() {
        if (addToWikiField != null && addToWikiField.length() > 0) {
            User user = (User) Helper.getManagedBeanValue("#{LoginForm.myBenutzer}");
            String message = this.addToWikiField + " (" + serviceManager.getUserService().getFullName(user) + ")";
            this.myProzess.setWikiField(
                    WikiFieldHelper.getWikiMessage(this.myProzess, this.myProzess.getWikiField(), "user", message));
            this.addToWikiField = "";
            try {
                serviceManager.getProcessService().save(myProzess);
            } catch (DataException e) {
                logger.error(e);
            }
        }
    }

    public ProcessProperty getProcessProperty() {
        return this.processProperty;
    }

    public void setProcessProperty(ProcessProperty processProperty) {
        this.processProperty = processProperty;
    }

    public List<ProcessProperty> getProcessProperties() {
        return this.processPropertyList;
    }

    private void loadProcessProperties() {
        try {
            this.myProzess = serviceManager.getProcessService().getById(this.myProzess.getId());
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn("could not refresh process with id " + this.myProzess.getId(), e);
            }
        }
        this.containers = new TreeMap<>();
        this.processPropertyList = PropertyParser.getPropertiesForProcess(this.myProzess);

        for (ProcessProperty pt : this.processPropertyList) {
            if (pt.getProzesseigenschaft() == null) {
                Property processProperty = new Property();
                processProperty.getProcesses().add(myProzess);
                pt.setProzesseigenschaft(processProperty);
                serviceManager.getProcessService().getPropertiesInitialized(myProzess).add(processProperty);
                pt.transfer();
            }
            if (!this.containers.keySet().contains(pt.getContainer())) {
                PropertyListObject plo = new PropertyListObject(pt.getContainer());
                plo.addToList(pt);
                this.containers.put(pt.getContainer(), plo);
            } else {
                PropertyListObject plo = this.containers.get(pt.getContainer());
                plo.addToList(pt);
                this.containers.put(pt.getContainer(), plo);
            }
        }
    }

    /**
     * TODO validierung nur bei Schritt abgeben, nicht bei normalen speichern.
     */
    public void saveProcessProperties() {
        boolean valid = true;
        for (IProperty p : this.processPropertyList) {
            if (!p.isValid()) {
                List<String> param = new ArrayList<>();
                param.add(p.getName());
                String value = Helper.getTranslation("propertyNotValid", param);
                Helper.setFehlerMeldung(value);
                valid = false;
            }
        }

        if (valid) {
            for (ProcessProperty p : this.processPropertyList) {
                if (p.getProzesseigenschaft() == null) {
                    Property processProperty = new Property();
                    processProperty.getProcesses().add(this.myProzess);
                    p.setProzesseigenschaft(processProperty);
                    serviceManager.getProcessService().getPropertiesInitialized(this.myProzess).add(processProperty);
                }
                p.transfer();
                if (!serviceManager.getProcessService().getPropertiesInitialized(this.myProzess)
                        .contains(p.getProzesseigenschaft())) {
                    serviceManager.getProcessService().getPropertiesInitialized(this.myProzess)
                            .add(p.getProzesseigenschaft());
                }
            }

            List<Property> props = this.myProzess.getProperties();
            for (Property processProperty : props) {
                if (processProperty.getTitle() == null) {
                    serviceManager.getProcessService().getPropertiesInitialized(this.myProzess).remove(processProperty);
                }
            }

            try {
                serviceManager.getProcessService().save(this.myProzess);
                Helper.setMeldung("Properties saved");
            } catch (DataException e) {
                logger.error(e);
                Helper.setFehlerMeldung("Properties could not be saved");
            }
        }
    }

    /**
     * Save current property.
     */
    public void saveCurrentProperty() {
        List<ProcessProperty> ppList = getContainerProperties();
        for (ProcessProperty pp : ppList) {
            this.processProperty = pp;
            if (isValidProcessProperty()) {
                if (this.processProperty.getProzesseigenschaft() == null) {
                    Property processProperty = new Property();
                    processProperty.getProcesses().add(this.myProzess);
                    this.processProperty.setProzesseigenschaft(processProperty);
                    serviceManager.getProcessService().getPropertiesInitialized(this.myProzess).add(processProperty);
                }
                this.processProperty.transfer();
                List<Property> props = this.myProzess.getProperties();
                for (Property processProperty : props) {
                    if (processProperty.getTitle() == null) {
                        serviceManager.getProcessService().getPropertiesInitialized(this.myProzess)
                                .remove(processProperty);
                    }
                }
                this.myProzess.getProperties().add(this.processProperty.getProzesseigenschaft());
                this.processProperty.getProzesseigenschaft().getProcesses().add(this.myProzess);
            } else {
                return;
            }

            try {
                serviceManager.getProcessService().save(this.myProzess);
                serviceManager.getPropertyService().save(this.processProperty.getProzesseigenschaft());
                Helper.setMeldung("propertiesSaved");
            } catch (DataException e) {
                logger.error(e);
                Helper.setFehlerMeldung("propertiesNotSaved");
            }
        }
        loadProcessProperties();
    }

    private boolean isValidProcessProperty() {
        if (this.processProperty.isValid()) {
            return true;
        } else {
            List<String> propertyNames = new ArrayList<>();
            propertyNames.add(processProperty.getName());
            String errorMessage = Helper.getTranslation("propertyNotValid", propertyNames);
            Helper.setFehlerMeldung(errorMessage);
            return false;
        }
    }

    /**
     * Get property list's size.
     *
     * @return size
     */
    public int getPropertyListSize() {
        if (this.processPropertyList == null) {
            return 0;
        }
        return this.processPropertyList.size();
    }

    public Map<Integer, PropertyListObject> getContainers() {
        return this.containers;
    }

    public List<Integer> getContainerList() {
        return new ArrayList<>(this.containers.keySet());
    }

    /**
     * Get containers' size.
     *
     * @return size
     */
    public int getContainersSize() {
        if (this.containers == null) {
            return 0;
        }
        return this.containers.size();
    }

    /**
     * Get sorted properties.
     *
     * @return list of ProcessProperty objects
     */
    public List<ProcessProperty> getSortedProperties() {
        Comparator<ProcessProperty> comp = new ProcessProperty.CompareProperties();
        Collections.sort(this.processPropertyList, comp);
        return this.processPropertyList;
    }

    /**
     * Delete property.
     */
    public void deleteProperty() {
        List<ProcessProperty> ppList = getContainerProperties();
        for (ProcessProperty pp : ppList) {
            this.processPropertyList.remove(pp);
            serviceManager.getProcessService().getPropertiesInitialized(this.myProzess)
                    .remove(pp.getProzesseigenschaft());

        }

        List<Property> props = this.myProzess.getProperties();
        for (Property processProperty : props) {
            if (processProperty.getTitle() == null) {
                serviceManager.getProcessService().getPropertiesInitialized(this.myProzess).remove(processProperty);
            }
        }
        try {
            serviceManager.getProcessService().save(this.myProzess);
        } catch (DataException e) {
            logger.error(e);
            Helper.setFehlerMeldung("propertiesNotDeleted");
        }
        // saveWithoutValidation();
        loadProcessProperties();
    }

    /**
     * Duplicate property.
     */
    public void duplicateProperty() {
        ProcessProperty pt = this.processProperty.getClone(0);
        this.processPropertyList.add(pt);
        saveProcessProperties();
    }

    public Integer getContainer() {
        return this.container;
    }

    /**
     * Set container.
     *
     * @param container
     *            Integer
     */
    public void setContainer(Integer container) {
        this.container = container;
        if (container != null && container > 0) {
            this.processProperty = getContainerProperties().get(0);
        }
    }

    /**
     * Get container properties.
     *
     * @return list of ProcessProperty objects
     */
    public List<ProcessProperty> getContainerProperties() {
        List<ProcessProperty> answer = new ArrayList<>();

        if (this.container != null && this.container > 0) {
            for (ProcessProperty pp : this.processPropertyList) {
                if (pp.getContainer() == this.container) {
                    answer.add(pp);
                }
            }
        } else {
            answer.add(this.processProperty);
        }

        return answer;
    }

    /**
     * Duplicate container.
     *
     * @return empty String
     */
    public String duplicateContainer() {
        Integer currentContainer = this.processProperty.getContainer();
        List<ProcessProperty> plist = new ArrayList<>();
        // search for all properties in container
        for (ProcessProperty pt : this.processPropertyList) {
            if (pt.getContainer() == currentContainer) {
                plist.add(pt);
            }
        }
        int newContainerNumber = 0;
        if (currentContainer > 0) {
            newContainerNumber++;
            // find new unused container number
            boolean search = true;
            while (search) {
                if (!this.containers.containsKey(newContainerNumber)) {
                    search = false;
                } else {
                    newContainerNumber++;
                }
            }
        }
        // clone properties
        for (ProcessProperty pt : plist) {
            ProcessProperty newProp = pt.getClone(newContainerNumber);
            this.processPropertyList.add(newProp);
            this.processProperty = newProp;
            if (this.processProperty.getProzesseigenschaft() == null) {
                Property processProperty = new Property();
                processProperty.getProcesses().add(this.myProzess);
                this.processProperty.setProzesseigenschaft(processProperty);
                serviceManager.getProcessService().getPropertiesInitialized(this.myProzess).add(processProperty);
            }
            this.processProperty.transfer();

        }
        try {
            serviceManager.getProcessService().save(this.myProzess);
            Helper.setMeldung("propertySaved");
        } catch (DataException e) {
            logger.error(e);
            Helper.setFehlerMeldung("propertiesNotSaved");
        }
        loadProcessProperties();

        return null;
    }

    /**
     * Get containerless properties.
     *
     * @return list of ProcessProperty objects
     */
    public List<ProcessProperty> getContainerlessProperties() {
        List<ProcessProperty> answer = new ArrayList<>();
        for (ProcessProperty pp : this.processPropertyList) {
            if (pp.getContainer() == 0) {
                answer.add(pp);
            }
        }
        return answer;
    }

    /**
     * Get list od DTO processes.
     *
     * @return list of ProcessDTO objects
     */
    public List<ProcessDTO> getProcessDTOS() {
        return processDTOS;
    }

    /**
     * Create new property.
     */
    public void createNewProperty() {
        if (this.processPropertyList == null) {
            this.processPropertyList = new ArrayList<>();
        }
        ProcessProperty pp = new ProcessProperty();
        pp.setType(Type.TEXT);
        pp.setContainer(0);
        this.processPropertyList.add(pp);
        this.processProperty = pp;
    }

    /**
     * Return the id of the current process.
     *
     * @return int processId
     */
    public int getProcessId() {
        return this.processId;
    }

    /**
     * Set the id of the current process.
     *
     * @param processId as int
     */
    public void setProcessId(int processId) {
        this.processId = processId;
    }

    /**
     * Method being used as viewAction for process edit form. If 'processId' is '0',
     * the form for creating a new process will be displayed.
     */
    public void loadMyProcess() {
        try {
            if (this.processId != 0) {
                setMyProzess(this.serviceManager.getProcessService().getById(this.processId));
            } else {
                newProcess();
            }
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error retrieving process with ID '" + this.processId + "'; ", e.getMessage());
        }
    }

    /**
     * Return the id of the current task.
     *
     * @return int taskId
     */
    public int getTaskId() {
        return this.taskId;
    }

    /**
     * Set the id of the current task.
     *
     * @param taskId as int
     */
    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    /**
     * Method being used as viewAction for task form.
     */
    public void loadMyTask() {
        try {
            if (!Objects.equals(this.taskId, null)) {
                setMySchritt(this.serviceManager.getTaskService().getById(this.taskId));
            }
        } catch (DAOException e) {
            Helper.setFehlerMeldung("Error retrieving task with ID '" + this.taskId + "'; ", e.getMessage());
        }
    }
}
