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

package org.kitodo.selenium.testframework.pages;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.kitodo.data.database.beans.User;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.selenium.testframework.Pages;
import org.kitodo.selenium.testframework.enums.TabIndex;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import static org.awaitility.Awaitility.await;
import static org.kitodo.selenium.testframework.Browser.hoverWebElement;

public class UserEditPage extends EditPage<UserEditPage> {

    private static final String USER_TAB_VIEW = EDIT_FORM + ":userTabView";
    private static final String CSS_SELECTOR_DROPDOWN_TRIGGER =  ".ui-selectonemenu-trigger";

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW)
    private WebElement userEditTabView;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":firstName")
    private WebElement firstNameInput;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":surname")
    private WebElement lastNameInput;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":login")
    private WebElement loginInput;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":password")
    private WebElement passwordInput;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":location")
    private WebElement locationInput;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":metaDataLanguage")
    private WebElement metadataLanguageSelect;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":table-size")
    private WebElement tableSizeInput;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":languages")
    private WebElement languageSelect;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":addRoleButton")
    private WebElement addUserToRoleButton;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":addProjectButton")
    private WebElement addUserToProjectButton;

    @SuppressWarnings("unused")
    @FindBy(id = USER_TAB_VIEW + ":addClientButton")
    private WebElement addUserToClientButton;

    @SuppressWarnings("unused")
    @FindBy(id = "roleForm:selectRoleTable_data")
    private WebElement selectRoleTable;

    @SuppressWarnings("unused")
    @FindBy(id = "userProjectForm:selectProjectTable_data")
    private WebElement selectProjectTable;

    @SuppressWarnings("unused")
    @FindBy(id = "userClientForm:selectClientTable_data")
    private WebElement selectClientTable;

    @SuppressWarnings("unused")
    @FindBy(id = "addRoleDialog")
    private WebElement addToRoleDialog;

    @SuppressWarnings("unused")
    @FindBy(id = "addProjectDialog")
    private WebElement addToProjectDialog;

    @SuppressWarnings("unused")
    @FindBy(id = "addClientDialog")
    private WebElement addToClientDialog;

    @SuppressWarnings("unused")
    @FindBy(id = "user-menu")
    private WebElement userMenuButton;

    @SuppressWarnings("unused")
    @FindBy(partialLinkText = "Benutzerdaten & Einstellungen")
    private WebElement userConfigButton;

    public UserEditPage() {
        super("pages/userEdit.jsf");
    }

    @Override
    public UserEditPage goTo() {
        return null;
    }

    public UserEditPage insertUserData(User user) {
        passwordInput.sendKeys(user.getPassword());
        firstNameInput.sendKeys(user.getName());
        lastNameInput.sendKeys(user.getSurname());
        loginInput.sendKeys(user.getLogin());
        locationInput.sendKeys(user.getLocation());
        clickElement(metadataLanguageSelect.findElement(By.cssSelector(CSS_SELECTOR_DROPDOWN_TRIGGER)));
        clickElement(Browser.getDriver().findElement(By.id(metadataLanguageSelect.getAttribute("id") + "_0")));
        return this;
    }

    public UsersPage save() throws IllegalAccessException, InstantiationException {
        clickButtonAndWaitForRedirect(saveButton, Pages.getUsersPage().getUrl());
        return Pages.getUsersPage();
    }

    public void addUserToRole(String roleTitle) throws Exception {
        switchToTabByIndex(TabIndex.USER_ROLES.getIndex());
        addUserToRoleButton.click();
        List<WebElement> tableRows = Browser.getRowsOfTable(selectRoleTable);
        addRow(tableRows, roleTitle, addToRoleDialog);
    }

    public void addUserToClient(String clientName) throws Exception {
        switchToTabByIndex(TabIndex.USER_CLIENT_LIST.getIndex());
        addUserToClientButton.click();
        List<WebElement> tableRows = Browser.getRowsOfTable(selectClientTable);
        addRow(tableRows, clientName, addToClientDialog);
    }

    public UserEditPage addUserToProject(String projectName) throws Exception {
        switchToTabByIndex(TabIndex.USER_PROJECT_LIST.getIndex());
        addUserToProjectButton.click();
        List<WebElement> tableRows = Browser.getRowsOfTable(selectProjectTable);
        addRow(tableRows, projectName, addToProjectDialog);
        return this;
    }

    public void changeUserSettings() throws Exception {
        openUserConfig();
        tableSizeInput.clear();
        tableSizeInput.sendKeys("50");
        clickElement(metadataLanguageSelect.findElement(By.cssSelector(CSS_SELECTOR_DROPDOWN_TRIGGER)));
        clickElement(Browser.getDriver().findElement(By.id(metadataLanguageSelect.getAttribute("id") + "_1")));
        clickElement(languageSelect.findElement(By.cssSelector(CSS_SELECTOR_DROPDOWN_TRIGGER)));
        clickElement(Browser.getDriver().findElement(By.id(languageSelect.getAttribute("id") + "_1")));
        save();
    }

    private void openUserConfig() {
        await("Wait for visible user menu button").atMost(20, TimeUnit.SECONDS).ignoreExceptions()
                .untilTrue(new AtomicBoolean(userMenuButton.isDisplayed()));

        hoverWebElement(userMenuButton);
        hoverWebElement(userConfigButton);
        userConfigButton.click();
    }

    /**
     * Clicks on the tab indicated by given index (starting with 0 for the first
     * tab).
     */
    private void switchToTabByIndex(int index) throws Exception {
        switchToTabByIndex(index, userEditTabView);
    }
}
