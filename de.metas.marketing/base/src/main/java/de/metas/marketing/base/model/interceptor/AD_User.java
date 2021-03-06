package de.metas.marketing.base.model.interceptor;

import java.util.Optional;

import org.adempiere.ad.modelvalidator.annotations.Interceptor;
import org.adempiere.ad.modelvalidator.annotations.ModelChange;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.user.User;
import org.adempiere.user.UserRepository;
import org.adempiere.util.Services;
import org.compiere.Adempiere;
import org.compiere.model.ModelValidator;
import org.springframework.stereotype.Component;

import de.metas.i18n.IMsgBL;
import de.metas.i18n.ITranslatableString;
import de.metas.marketing.base.CampaignService;
import de.metas.marketing.base.model.CampaignId;
import de.metas.marketing.base.model.CampaignRepository;
import de.metas.marketing.base.model.I_AD_User;
import lombok.NonNull;

/*
 * #%L
 * marketing-base
 * %%
 * Copyright (C) 2018 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
@Interceptor(I_AD_User.class)
@Component
public class AD_User
{
	private final static String MRG_MKTG_Campaign_NewsletterGroup_Missing_For_Org = "MKTG_Campaign_NewsletterGroup_Missing_For_Org";

	private final UserRepository userRepository;

	private AD_User(@NonNull final UserRepository userRepository)
	{
		this.userRepository = userRepository;
	}

	@ModelChange(timings = { ModelValidator.TYPE_AFTER_NEW, ModelValidator.TYPE_AFTER_CHANGE }, ifColumnsChanged = { I_AD_User.COLUMNNAME_IsNewsletter })
	public void onChangeNewsletter(final I_AD_User userRecord)
	{
		final IMsgBL msgBL = Services.get(IMsgBL.class);

		final CampaignRepository campaignRepository = Adempiere.getBean(CampaignRepository.class);
		final CampaignService converters = Adempiere.getBean(CampaignService.class);

		final boolean isNewsletter = userRecord.isNewsletter();

		final Optional<CampaignId> defaultcampaignId = campaignRepository.getDefaultNewsletterCampaignId(userRecord.getAD_Org_ID());

		if (isNewsletter)
		{
			if (!defaultcampaignId.isPresent())
			{
				final ITranslatableString translatableMsgText = msgBL.getTranslatableMsgText(
						MRG_MKTG_Campaign_NewsletterGroup_Missing_For_Org,
						userRecord.getAD_Org().getName());

				throw new AdempiereException(translatableMsgText);
			}
			final User user = userRepository.ofRecord(userRecord);
			converters.addToCampaignIfHasEmailAddress(user, defaultcampaignId.get());
		}
		else
		{
			if (!defaultcampaignId.isPresent())
			{
				return; // nothing to do
			}
			final User user = userRepository.ofRecord(userRecord);
			converters.removeFromCampaign(user, defaultcampaignId.get());
		}
	}
}
