/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.security.authentication.test.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.xwiki.administration.test.po.AccountValidationPage;
import org.xwiki.test.docker.junit5.TestConfiguration;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.integration.junit.LogCaptureConfiguration;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.LoginPage;
import org.xwiki.test.ui.po.RegistrationPage;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

import static org.apache.commons.lang3.RandomStringUtils.secure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify that a newly registered account can be validated using the link sent by email, in particular when the wiki
 * prevents unregistered users from viewing pages (see XWIKI-5143).
 *
 * @version $Id$
 * @since 18.6.0RC1
 */
@UITest(sshPorts = {
    // Open the GreenMail port so that the XWiki instance inside a Docker container can use the SMTP server provided
    // by GreenMail running on the host.
    3025
    },
    properties = {
        // The Mail module contributes a Hibernate mapping that needs to be added to hibernate.cfg.xml
        "xwikiDbHbmCommonExtraMappings=mailsender.hbm.xml,notification-filter-preferences.hbm.xml",
    },
    extraJARs = {
        // It's currently not possible to install a JAR contributing a Hibernate mapping file as an Extension. Thus
        // we need to provide the JAR inside WEB-INF/lib. See https://jira.xwiki.org/browse/XWIKI-19932
        "org.xwiki.platform:xwiki-platform-mail-send-storage",
        "org.xwiki.platform:xwiki-platform-notifications-filters-default"
    }
)
class AccountValidationIT
{
    private GreenMail mail;

    private String userName;

    @BeforeEach
    void startMail(TestUtils setup, TestConfiguration testConfiguration) throws Exception
    {
        this.mail = new GreenMail(ServerSetupTest.SMTP);
        this.mail.start();

        setup.loginAsSuperAdmin();
        setup.updateObject("Mail", "MailConfig", "Mail.SendMailConfigClass", 0, "host",
            testConfiguration.getServletEngine().getHostIP(), "port", "3025", "sendWaitTime", "0");

        // Require email validation for newly registered accounts, and prevent unregistered users from viewing
        // pages: this exact combination used to make it impossible to follow the validation link, since it pointed
        // to a regular wiki page that guests were no longer allowed to view (XWIKI-5143).
        setup.setWikiPreference("use_email_verification", "1");
        setup.setWikiPreference("authenticate_view", "1");
    }

    @AfterEach
    void stopMail(TestUtils setup, LogCaptureConfiguration logCaptureConfiguration) throws Exception
    {
        if (this.mail != null) {
            this.mail.stop();
        }

        setup.loginAsSuperAdmin();
        setup.setWikiPreference("use_email_verification", "0");
        setup.setWikiPreference("authenticate_view", "0");
        setup.deleteLatestVersion("Mail", "MailConfig");
        if (this.userName != null) {
            setup.rest().deletePage("XWiki", this.userName);
        }
        logCaptureConfiguration.registerExcludes("Secret CSRF token verification failed");
    }

    @Test
    void validateAccountOnPrivateWiki(TestUtils setup) throws Exception
    {
        this.userName = "testUser" + secure().nextAlphanumeric(6);
        String password = "password";

        setup.forceGuestUser();
        RegistrationPage registrationPage = RegistrationPage.gotoPage();
        registrationPage.fillRegisterForm("John", "Doe", this.userName, password, password,
            this.userName + "@localhost.localdomain");
        registrationPage.clickRegister();
        assertTrue(registrationPage.getRegistrationSuccessMessage().isPresent());

        // The account is inactive until the validation link is followed, so logging in must fail.
        setup.forceGuestUser();
        LoginPage loginPage = LoginPage.gotoPage();
        loginPage.loginAs(this.userName, password);
        assertFalse(this.userName.equals(setup.getLoggedInUserName()));

        // Retrieve the validation link from the email that was sent.
        assertTrue(this.mail.waitForIncomingEmail(1));
        MimeMessage[] receivedEmails = this.mail.getReceivedMessages();
        assertEquals(1, receivedEmails.length);
        String validationLink = getValidationLink(getTextContent(receivedEmails[0]));

        // Follow the link while logged out: this must succeed even though the wiki prevents unregistered users
        // from viewing pages, since the validation resource bypasses the normal page-view rights check, unlike
        // the old XWiki.AccountValidation page it replaces.
        setup.forceGuestUser();
        setup.gotoPage(validationLink);
        AccountValidationPage accountValidationPage = new AccountValidationPage();
        assertTrue(accountValidationPage.isAccountValidated(), accountValidationPage.getMessage());

        // The account can now log in.
        loginPage = LoginPage.gotoPage();
        loginPage.loginAs(this.userName, password);
        assertEquals(this.userName, setup.getLoggedInUserName());
    }

    private String getTextContent(MimeMessage message) throws Exception
    {
        Object content = message.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType("text/plain")) {
                    return IOUtils.toString(part.getInputStream(), "UTF-8");
                }
            }
            return "";
        }
        return String.valueOf(content);
    }

    private String getValidationLink(String emailContent)
    {
        Pattern pattern = Pattern.compile("https?://\\S*validateaccount\\?\\S+");
        Matcher matcher = pattern.matcher(emailContent);
        if (matcher.find()) {
            return matcher.group();
        }

        throw new AssertionFailedError(
            String.format("Cannot find the validation link in the email content: [%s]", emailContent));
    }
}
