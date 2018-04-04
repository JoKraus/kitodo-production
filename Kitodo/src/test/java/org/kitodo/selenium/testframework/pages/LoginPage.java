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

import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.selenium.testframework.Browser;
import org.kitodo.services.ServiceManager;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class LoginPage {

    private final String URL = "pages/login.jsf";

    @SuppressWarnings("unused")
    @FindBy(id = "login")
    private WebElement loginButton;

    @SuppressWarnings("unused")
    @FindBy(id = "username")
    private WebElement usernameInput;

    @SuppressWarnings("unused")
    @FindBy(id = "password")
    private WebElement passwordInput;

    /**
     * Goes to login page.
     *
     * @return The login page.
     */
    public LoginPage goTo() {
        Browser.goTo(URL);
        return this;
    }

    public void performLogin(User user) throws InterruptedException {
        usernameInput.clear();
        usernameInput.sendKeys(user.getLogin());

        passwordInput.clear();
        passwordInput.sendKeys(user.getPassword());

        loginButton.click();
        Thread.sleep(Browser.getDelayAfterLogin());
    }

    public void performLoginAsAdmin() throws InterruptedException, DAOException {
        User user = new ServiceManager().getUserService().getById(1);
        user.setPassword("test");
        performLogin(user);
    }
}
