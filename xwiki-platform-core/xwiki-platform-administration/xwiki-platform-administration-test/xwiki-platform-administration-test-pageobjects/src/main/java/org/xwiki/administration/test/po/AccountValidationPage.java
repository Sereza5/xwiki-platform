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
package org.xwiki.administration.test.po;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.xwiki.test.ui.po.ViewPage;

/**
 * Represents the result page reached by following the account validation link sent by email after registering,
 * handled by the {@code validateaccount} authentication resource action.
 *
 * @version $Id$
 * @since 18.6.0RC1
 */
public class AccountValidationPage extends ViewPage
{
    /**
     * Resource action used for the account validation handling.
     */
    public static final String ACCOUNT_VALIDATION_URL_RESOURCE = "authenticate/wiki/%s/validateaccount";

    @FindBy(css = ".xwikimessage")
    private WebElement messageBox;

    public static String getAccountValidationURL()
    {
        return getUtil().getBaseURL() + String.format(ACCOUNT_VALIDATION_URL_RESOURCE, getUtil().getCurrentWiki());
    }

    public String getMessage()
    {
        return this.messageBox.getText();
    }

    /**
     * @return {@code true} if the account was successfully validated
     */
    public boolean isAccountValidated()
    {
        return this.messageBox.getText().contains("Your account has been activated");
    }
}
