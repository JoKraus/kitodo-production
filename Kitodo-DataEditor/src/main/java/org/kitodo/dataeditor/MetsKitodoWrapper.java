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

package org.kitodo.dataeditor;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.transform.TransformerException;

import org.kitodo.dataeditor.handlers.MetsKitodoFileSecHandler;
import org.kitodo.dataeditor.handlers.MetsKitodoMdSecHandler;
import org.kitodo.dataeditor.handlers.MetsKitodoStructMapHandler;
import org.kitodo.dataformat.metskitodo.KitodoType;
import org.kitodo.dataformat.metskitodo.MdSecType;
import org.kitodo.dataformat.metskitodo.Mets;
import org.kitodo.dataformat.metskitodo.MetsType;
import org.kitodo.dataformat.metskitodo.StructLinkType;
import org.kitodo.dataformat.metskitodo.StructMapType;

/**
 * This is a wrapper class for holding and manipulating the content of a
 * serialized mets-kitodo format xml file.
 */
public class MetsKitodoWrapper {

    private Mets mets;
    private MetsKitodoObjectFactory objectFactory = new MetsKitodoObjectFactory();

    /**
     * Gets the mets object.
     *
     * @return The mets object.
     */
    public Mets getMets() {
        return mets;
    }

    /**
     * Constructor which creates a Mets object with corresponding object factory and
     * also inserts the basic mets elements (FileSec with local file group, StructLink, MetsHdr, physical
     * and logical StructMap).
     */
    public MetsKitodoWrapper() throws DatatypeConfigurationException, IOException {
        this.mets = createBasicMetsElements(objectFactory.createMets());
    }

    private Mets createBasicMetsElements(Mets mets) throws DatatypeConfigurationException, IOException {
        if (Objects.isNull(mets.getFileSec())) {
            mets.setFileSec(objectFactory.createMetsTypeFileSec());
            MetsType.FileSec.FileGrp fileGroup = objectFactory.createMetsTypeFileSecFileGrpLocal();
            mets.getFileSec().getFileGrp().add(fileGroup);
        }
        if (Objects.isNull(mets.getStructLink())) {
            mets.setStructLink(objectFactory.createMetsTypeStructLink());
        }
        if (Objects.isNull(mets.getMetsHdr())) {
            mets.setMetsHdr(objectFactory.createKitodoMetsHeader());
        }
        if (mets.getStructMap().isEmpty()) {
            StructMapType logicalStructMapType = objectFactory.createLogicalStructMapType();
            mets.getStructMap().add(logicalStructMapType);

            StructMapType physicalStructMapType = objectFactory.createPhysicalStructMapType();
            mets.getStructMap().add(physicalStructMapType);
        }
        return mets;
    }

    /**
     * Constructor which creates Mets object by unmarshalling given xml file of
     * mets-kitodo format.
     * 
     * @param xmlFile
     *            The xml file in mets-kitodo format as URI.
     * @param xsltFile
     *            The URI to the xsl file for transformation of old format goobi
     *            metadata files.
     */
    public MetsKitodoWrapper(URI xmlFile, URI xsltFile)
            throws JAXBException, TransformerException, IOException, DatatypeConfigurationException {
        this.mets = createBasicMetsElements(MetsKitodoReader.readAndValidateUriToMets(xmlFile, xsltFile));
    }

    /**
     * Adds a smLink to the structLink section of mets file.
     * 
     * @param from
     *            The from value.
     * @param to
     *            The to value.
     */
    public void addSmLink(String from, String to) {
        StructLinkType.SmLink structLinkTypeSmLink = objectFactory.createStructLinkTypeSmLink();
        structLinkTypeSmLink.setFrom(from);
        structLinkTypeSmLink.setTo(to);
        mets.getStructLink().getSmLinkOrSmLinkGrp().add(structLinkTypeSmLink);
    }

    /**
     * Gets all dmdSec elements.
     *
     * @return All dmdSec elements as list of MdSecType objects.
     */
    public List<MdSecType> getDmdSecs() {
        return this.mets.getDmdSec();
    }

    /**
     * Gets KitodoType object of specified MdSec index.
     * 
     * @param index
     *            The index as int.
     * @return The KitodoType object.
     */
    public KitodoType getKitodoTypeByMdSecIndex(int index) {
        if (this.mets.getDmdSec().size() > index) {
            List<Object> xmlData = getXmlDataByMdSecIndex(index);
            try {
                return MetsKitodoMdSecHandler.getFirstGenericTypeFromJaxbObjectList(xmlData, KitodoType.class);
            } catch (NoSuchElementException e) {
                throw new NoSuchElementException(
                        "MdSec element with index: " + index + " does not have kitodo metadata");
            }
        }
        throw new NoSuchElementException("MdSec element with index: " + index + " does not exist");
    }

    /**
     * Gets xml data object of specified MdSec index.
     *
     * @param index
     *            The index as int.
     * @return The KitodoType object.
     */
    private List<Object> getXmlDataByMdSecIndex(int index) {
        return MetsKitodoMdSecHandler.getXmlDataOfMetsByMdSecIndex(this.mets, index);
    }

    /**
     * Gets KitodoType object of specified MdSec id.
     *
     * @param id
     *            The id as String.
     * @return The KitodoType object.
     */
    public KitodoType getKitodoTypeByMdSecId(String id) {
        int index = 0;
        for (MdSecType mdSecType : this.mets.getDmdSec()) {
            if (mdSecType.getID().equals(id)) {
                return getKitodoTypeByMdSecIndex(index);
            }
            index++;
        }
        throw new NoSuchElementException("MdSec element with id: " + id + " was not found");
    }

    /**
     * Inserts MediaFile objects into fileSec of mets document and creates
     * corresponding physical structMap.
     * 
     * @param files
     *            The list of MediaFile objects.
     */
    public void insertMediaFiles(List<MediaFile> files) {
        MetsKitodoFileSecHandler.insertMediaFilesToLocalFileGroupOfMets(this.mets, files);
        //TODO implement logic to check if pagination is set to automatic or not
        MetsKitodoStructMapHandler.fillPhysicalStructMapByMetsFileSec(mets);
    }

    /**
     * Returns the physical StructMap of mets document.
     * 
     * @return The StructMapType object.
     */
    public StructMapType getPhysicalStructMap() {
        return MetsKitodoStructMapHandler.getMetsStructMapByType(mets, "PHYSICAL");
    }

    /**
     * Returns the logical StructMap of mets document.
     * 
     * @return The StructMapType object.
     */
    public StructMapType getLogicalStructMap() {
        return MetsKitodoStructMapHandler.getMetsStructMapByType(mets, "LOGICAL");
    }
}