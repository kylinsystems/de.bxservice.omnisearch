/**********************************************************************
 * Copyright (C) Contributors                                          *
 *                                                                     *
 * This program is free software; you can redistribute it and/or       *
 * modify it under the terms of the GNU General Public License         *
 * as published by the Free Software Foundation; either version 2      *
 * of the License, or (at your option) any later version.              *
 *                                                                     *
 * This program is distributed in the hope that it will be useful,     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of      *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the        *
 * GNU General Public License for more details.                        *
 *                                                                     *
 * You should have received a copy of the GNU General Public License   *
 * along with this program; if not, write to the Free Software         *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,          *
 * MA 02110-1301, USA.                                                 *
 *                                                                     *
 * Contributors:                                                       *
 * - Diego Ruiz - BX Service GmbH                                      *
 **********************************************************************/
package de.bxservice.omnisearch.validator;

import java.util.List;
import java.util.Set;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventManager;
import org.adempiere.base.event.IEventTopics;
import org.compiere.model.MColumn;
import org.compiere.model.PO;
import org.compiere.util.CLogger;
import org.osgi.service.event.Event;

import de.bxservice.omniimpl.TextSearchValues;
import de.bxservice.omnisearch.tools.OmnisearchAbstractFactory;
import de.bxservice.omnisearch.tools.OmnisearchHelper;

public class TSearchIndexEventHandler extends AbstractEventHandler {

	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(TSearchIndexEventHandler.class);
	
	private String       trxName      = null;
	private Set<String>  fkTableNames = null;

	@Override
	protected void initialize() {
		log.warning("");

		List<String> indexedTables = OmnisearchHelper.getIndexedTableNames(TextSearchValues.TS_INDEX_NAME, trxName);

		for (String tableName : indexedTables) {
			registerTableEvent(IEventTopics.PO_AFTER_NEW, tableName);
			registerTableEvent(IEventTopics.PO_AFTER_CHANGE, tableName);
			registerTableEvent(IEventTopics.PO_AFTER_DELETE, tableName);
		}

		fkTableNames = OmnisearchHelper.getForeignTableNames(TextSearchValues.TS_INDEX_NAME, trxName);
		//Index the FK tables
		for (String tableName : fkTableNames) {
			//Don't duplicate the Event for the same table
			if (!indexedTables.contains(tableName))
				registerTableEvent(IEventTopics.PO_AFTER_CHANGE, tableName);
		}

		//Handle the changes in MColumn to update the index
		registerTableEvent(IEventTopics.PO_AFTER_NEW, MColumn.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, MColumn.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_DELETE, MColumn.Table_Name);
	}

	@Override
	protected void doHandleEvent(Event event) {
		String type = event.getTopic();
		PO po = getPO(event);
		trxName = po.get_TrxName();
		
		if (po instanceof MColumn) {
			if ((type.equals(IEventTopics.PO_AFTER_CHANGE) &&
					po.is_ValueChanged(TextSearchValues.TS_INDEX_NAME)) || 
					(po.get_ValueAsBoolean(TextSearchValues.TS_INDEX_NAME))) {
				//If the Text search index flag is changed -> register/unregister the modified table
				IEventManager tempManager = eventManager;
				unbindEventManager(eventManager);
				bindEventManager(tempManager);
			}
		} else if (type.equals(IEventTopics.PO_AFTER_DELETE))
			OmnisearchHelper.deleteFromDocument(OmnisearchAbstractFactory.TEXTSEARCH_INDEX, po);
		else 
			OmnisearchHelper.updateDocument(OmnisearchAbstractFactory.TEXTSEARCH_INDEX, po, 
					type.equals(IEventTopics.PO_AFTER_NEW));
		
		if (fkTableNames.contains(po.get_TableName())) {
			OmnisearchHelper.updateParent(OmnisearchAbstractFactory.TEXTSEARCH_INDEX, po);
		}
	}

}
