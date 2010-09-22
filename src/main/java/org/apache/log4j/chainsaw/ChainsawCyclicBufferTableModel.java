/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j.chainsaw;

import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.ProgressMonitor;
import javax.swing.event.EventListenerList;
import javax.swing.table.AbstractTableModel;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.chainsaw.color.RuleColorizer;
import org.apache.log4j.chainsaw.helper.SwingHelper;
import org.apache.log4j.helpers.Constants;
import org.apache.log4j.rule.Rule;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;


/**
 * A CyclicBuffer implementation of the EventContainer.
 *
 * NOTE:  This implementation prevents duplicate rows from being added to the model.
 *
 * Ignoring duplicates was added to support receivers which may attempt to deliver the same
 * event more than once but can be safely ignored (for example, the database receiver
 * when set to retrieve in a loop).
 *
 * @author Paul Smith <psmith@apache.org>
 * @author Scott Deboy <sdeboy@apache.org>
 * @author Stephen Pain
 *
 */
class ChainsawCyclicBufferTableModel extends AbstractTableModel
  implements EventContainer, PropertyChangeListener {

  private static final int DEFAULT_CAPACITY = 5000;
  //cyclic field used internally in this class, but not exposed via the eventcontainer
  private boolean cyclic = true;
  private int cyclicBufferSize = DEFAULT_CAPACITY;
  List unfilteredList;
  List filteredList;
  private boolean currentSortAscending;
  private int currentSortColumn;
  private final EventListenerList eventListenerList = new EventListenerList();
  private final List columnNames = new ArrayList(ChainsawColumns.getColumnsNames());
  private boolean sortEnabled = false;
  private boolean reachedCapacity = false;
  private final Logger logger = LogManager.getLogger(ChainsawCyclicBufferTableModel.class);

  //  protected final Object syncLock = new Object();
  private final LoggerNameModel loggerNameModelDelegate = new LoggerNameModelSupport();
  private final Object mutex = new Object();

  //because we may be using a cyclic buffer, if an ID is not provided in the property, 
  //use and increment this row counter as the ID for each received row
  int uniqueRow;
  private final Set uniquePropertyKeys = new HashSet();
  private Rule displayRule;
  private final PropertyChangeSupport propertySupport = new PropertyChangeSupport(this);
  private RuleColorizer colorizer;

    public ChainsawCyclicBufferTableModel(int cyclicBufferSize, RuleColorizer colorizer) {
    propertySupport.addPropertyChangeListener("cyclic", new ModelChanger());
    this.cyclicBufferSize = cyclicBufferSize;
    this.colorizer = colorizer;

    unfilteredList = new CyclicBufferList(cyclicBufferSize);
    filteredList = new CyclicBufferList(cyclicBufferSize);
  }

  /* (non-Javadoc)
   * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
   */
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getSource() instanceof Rule) {
      reFilter();
    }
  }

  public List getMatchingEvents(Rule rule) {

    List list = new ArrayList();
    List unfilteredCopy;
    synchronized (mutex) {
        unfilteredCopy = new ArrayList(unfilteredList);
    }
    Iterator iter = unfilteredCopy.iterator();

    while (iter.hasNext()) {
      LoggingEvent event = (LoggingEvent) iter.next();

      if (rule.evaluate(event, null)) {
        list.add(event);
      }
    }

    return list;
  }

  public void reFilter() {
    final int previousSize;
    final int newSize;
          synchronized (mutex) {
            //post refilter with newValue of TRUE (filtering is about to begin)
            propertySupport.firePropertyChange("refilter", Boolean.FALSE, Boolean.TRUE);
            previousSize = filteredList.size();
            filteredList.clear();
            if (displayRule == null) {
                LoggingEvent lastEvent = null;
                for (Iterator iter = unfilteredList.iterator();iter.hasNext();) {
                    ExtendedLoggingEvent e = (ExtendedLoggingEvent)iter.next();
                    e.setDisplayed(true);
                    updateEventMillisDelta(e, lastEvent);
                    filteredList.add(e);
                    lastEvent = e;
                }
            } else {
                Iterator iter = unfilteredList.iterator();
                LoggingEvent lastEvent = null;
                while (iter.hasNext()) {
                  ExtendedLoggingEvent e = (ExtendedLoggingEvent) iter.next();

                  if (displayRule.evaluate(e, null)) {
                    e.setDisplayed(true);
                    filteredList.add(e);
                    updateEventMillisDelta(e, lastEvent);
                    lastEvent = e;
                  } else {
                    e.setDisplayed(false);
                  }
                }
            }
            newSize = filteredList.size();
          }
      	SwingHelper.invokeOnEDT(new Runnable() {
      		public void run() {
      			if (newSize > 0) {
	      			if (previousSize == newSize) {
	      				//same - update all
	      				fireTableRowsUpdated(0, newSize - 1);
	      			} else if (previousSize > newSize) {
	      				//less now..update and delete difference
	      				fireTableRowsUpdated(0, newSize - 1);
                        //swing bug exposed by variable height rows when calling fireTableRowsDeleted..use tabledatacchanged
                        fireTableDataChanged();
	      			} else if (previousSize < newSize) {
	      				//more now..update and insert difference
                        if (previousSize > 0) {
	      				    fireTableRowsUpdated(0, previousSize - 1);
                        }
	      				fireTableRowsInserted(Math.max(0, previousSize), newSize - 1);
	      			}
      			} else {
      				//no rows to show
      				fireTableDataChanged();
      			}
	      	notifyCountListeners();
            //post refilter with newValue of FALSE (filtering is complete) (enqueue on EDT, don't run now)
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    propertySupport.firePropertyChange("refilter", Boolean.TRUE, Boolean.FALSE);
                }
            });
      	}});
  }

  public int locate(Rule rule, int startLocation, boolean searchForward) {
    List filteredListCopy;
    synchronized (mutex) {
      filteredListCopy = new ArrayList(filteredList);
    }
      if (searchForward) {
        for (int i = startLocation; i < filteredListCopy.size(); i++) {
          if (rule.evaluate((LoggingEvent) filteredListCopy.get(i), null)) {
            return i;
          }
        }
        //if there was no match, start at row zero and go to startLocation
        for (int i = 0; i < startLocation; i++) {
          if (rule.evaluate((LoggingEvent) filteredListCopy.get(i), null)) {
            return i;
          }
        }
      } else {
        for (int i = startLocation; i > -1; i--) {
          if (rule.evaluate((LoggingEvent) filteredListCopy.get(i), null)) {
            return i;
          }
        }
        //if there was no match, start at row list.size() - 1 and go to startLocation
        for (int i = filteredListCopy.size() - 1; i > startLocation; i--) {
          if (rule.evaluate((LoggingEvent) filteredListCopy.get(i), null)) {
            return i;
          }
        }
      }

    return -1;
  }

  /**
   * @param l
   */
  public void removeLoggerNameListener(LoggerNameListener l) {
    loggerNameModelDelegate.removeLoggerNameListener(l);
  }

  /**
   * @param loggerName
   * @return
   */
  public boolean addLoggerName(String loggerName) {
    return loggerNameModelDelegate.addLoggerName(loggerName);
  }

  public void reset() {
      loggerNameModelDelegate.reset();
  }

  /**
   * @param l
   */
  public void addLoggerNameListener(LoggerNameListener l) {
    loggerNameModelDelegate.addLoggerNameListener(l);
  }

  /**
   * @return
   */
  public Collection getLoggerNames() {
    return loggerNameModelDelegate.getLoggerNames();
  }

  public void addEventCountListener(EventCountListener listener) {
    eventListenerList.add(EventCountListener.class, listener);
  }

  public boolean isSortable(int col) {
    return true;
  }

  public void notifyCountListeners() {
    EventCountListener[] listeners =
      (EventCountListener[]) eventListenerList.getListeners(
        EventCountListener.class);

    int filteredListSize;
    int unfilteredListSize;
    synchronized (mutex) {
        filteredListSize = filteredList.size();
        unfilteredListSize = unfilteredList.size();
    }
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].eventCountChanged(
        filteredListSize, unfilteredListSize);
    }
  }

  /**
   * Changes the underlying display rule in use.  If there was
   * a previous Rule defined, this Model removes itself as a listener
   * from the old rule, and adds itself to the new rule (if the new Rule is not Null).
   *
   * In any case, the model ensures the Filtered list is made up to date in a separate thread.
   */
  public void setDisplayRule(Rule displayRule) {
    if (this.displayRule != null) {
      this.displayRule.removePropertyChangeListener(this);
    }

    this.displayRule = displayRule;

    if (this.displayRule != null) {
      this.displayRule.addPropertyChangeListener(this);
    }

    reFilter();
  }

  /* (non-Javadoc)
     * @see org.apache.log4j.chainsaw.EventContainer#sort()
     */
  public void sort() {
      boolean sort;
      final int filteredListSize;
      synchronized (mutex) {
          filteredListSize = filteredList.size();
          sort = (sortEnabled && filteredListSize > 0);
        if (sort) {
            //reset display (used to ensure row height is updated)
            LoggingEvent lastEvent = null;
            for (Iterator iter = filteredList.iterator();iter.hasNext();) {
                ExtendedLoggingEvent e = (ExtendedLoggingEvent)iter.next();
                e.setDisplayed(true);
                updateEventMillisDelta(e, lastEvent);
                lastEvent = e;
            }
            Collections.sort(
              filteredList,
              new ColumnComparator(
                getColumnName(currentSortColumn), currentSortColumn,
                currentSortAscending));
        }
      }
      if (sort) {
        SwingHelper.invokeOnEDT(new Runnable() {
            public void run() {
                fireTableRowsUpdated(0, Math.max(filteredListSize - 1, 0));
            }
        });
      }
  }

  public boolean isSortEnabled() {
    return sortEnabled;
  }

  public void sortColumn(int col, boolean ascending) {
    logger.debug("request to sort col=" + col);
    currentSortAscending = ascending;
    currentSortColumn = col;
    sortEnabled = true;
    sort();
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#clear()
   */
  public void clearModel() {
    reachedCapacity = false;

    synchronized (mutex) {
      unfilteredList.clear();
      filteredList.clear();
      uniqueRow = 0;
    }

    SwingHelper.invokeOnEDT(new Runnable() {
    	public void run() {
    	    fireTableDataChanged();
    	}
    });

    notifyCountListeners();
    loggerNameModelDelegate.reset();
  }

  public List getAllEvents() {
      synchronized (mutex) {
          return new ArrayList(unfilteredList);
      }
  }
  
  
  public List getFilteredEvents() {

  	synchronized (mutex) {
  		return new ArrayList(filteredList);
  	}
  }
  
  public int getRowIndex(LoggingEvent e) {
    synchronized (mutex) {
      return filteredList.indexOf(e);
    }
  }

    public void removePropertyFromEvents(String propName) {
        //first remove the event from any displayed events, so we can fire row updated event
        List filteredListCopy;
        List unfilteredListCopy;
        synchronized(mutex) {
            filteredListCopy = new ArrayList(filteredList);
            unfilteredListCopy = new ArrayList(unfilteredList);
        }
        for (int i=0;i<filteredListCopy.size();i++) {
            LoggingEvent event = (LoggingEvent)filteredListCopy.get(i);
            Object result = event.removeProperty(propName);
            if (result != null) {
                fireRowUpdated(i, false);
            }
        }
        //now remove the event from all events
        for (Iterator iter = unfilteredListCopy.iterator();iter.hasNext();) {
            LoggingEvent event = (LoggingEvent)iter.next();
            event.removeProperty(propName);
        }
    }

    public int updateEventsWithFindRule(Rule findRule) {
        int count = 0;
        List unfilteredListCopy;
        synchronized(mutex) {
            unfilteredListCopy = new ArrayList(unfilteredList);
        }
        for (Iterator iter = unfilteredListCopy.iterator();iter.hasNext();) {
            ExtendedLoggingEvent extendedLoggingEvent = (ExtendedLoggingEvent) iter.next();
            extendedLoggingEvent.evaluateSearchRule(findRule);
            if (extendedLoggingEvent.isSearchMatch()) {
                count++;
            }
        }
        return count;
    }

    public int findColoredRow(int startLocation, boolean searchForward) {
        List filteredListCopy;
        synchronized (mutex) {
            filteredListCopy = new ArrayList(filteredList);
        }
        if (searchForward) {
          for (int i = startLocation; i < filteredListCopy.size(); i++) {
            ExtendedLoggingEvent event = (ExtendedLoggingEvent)filteredListCopy.get(i);
            if (!event.getColorRuleBackground().equals(ChainsawConstants.COLOR_DEFAULT_BACKGROUND) ||
                    !event.getColorRuleForeground().equals(ChainsawConstants.COLOR_DEFAULT_FOREGROUND)) {
                return i;
            }
          }
          //searching forward, no colorized event was found - now start at row zero and go to startLocation
          for (int i = 0; i < startLocation; i++) {
            ExtendedLoggingEvent event = (ExtendedLoggingEvent)filteredListCopy.get(i);
            if (!event.getColorRuleBackground().equals(ChainsawConstants.COLOR_DEFAULT_BACKGROUND) ||
                    !event.getColorRuleForeground().equals(ChainsawConstants.COLOR_DEFAULT_FOREGROUND)) {
                return i;
            }
          }
        } else {
          for (int i = startLocation; i > -1; i--) {
              ExtendedLoggingEvent event = (ExtendedLoggingEvent)filteredListCopy.get(i);
              if (!event.getColorRuleBackground().equals(ChainsawConstants.COLOR_DEFAULT_BACKGROUND) ||
                      !event.getColorRuleForeground().equals(ChainsawConstants.COLOR_DEFAULT_FOREGROUND)) {
                  return i;
            }
          }
          //searching backward, no colorized event was found - now start at list.size() - 1 and go to startLocation
          for (int i = filteredListCopy.size() - 1; i > startLocation; i--) {
              ExtendedLoggingEvent event = (ExtendedLoggingEvent)filteredListCopy.get(i);
              if (!event.getColorRuleBackground().equals(ChainsawConstants.COLOR_DEFAULT_BACKGROUND) ||
                      !event.getColorRuleForeground().equals(ChainsawConstants.COLOR_DEFAULT_FOREGROUND)) {
                  return i;
            }
          }
        }

      return -1;
    }

    public int getColumnCount() {
    return columnNames.size();
  }

  public String getColumnName(int column) {
      return (String) columnNames.get(column);
  }

  public ExtendedLoggingEvent getRow(int row) {
    synchronized (mutex) {
      if (row < filteredList.size() && row > -1) {
        return (ExtendedLoggingEvent) filteredList.get(row);
      }
    }

    return null;
  }

  public int getRowCount() {
    synchronized (mutex) {
      return filteredList.size();
    }
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    LoggingEvent event = null;

    synchronized (mutex) {
      if (rowIndex < filteredList.size() && rowIndex > -1) {
        event = (LoggingEvent) filteredList.get(rowIndex);
      }
    }

    if (event == null) {
      return null;
    }

    LocationInfo info = null;

    if (event.locationInformationExists()) {
      info = event.getLocationInformation();
    }

    switch (columnIndex + 1) {
    case ChainsawColumns.INDEX_ID_COL_NAME:

      Object id = event.getProperty(Constants.LOG4J_ID_KEY);

      if (id != null) {
        return id;
      }

      return new Integer(rowIndex);

    case ChainsawColumns.INDEX_LEVEL_COL_NAME:
      return event.getLevel();

    case ChainsawColumns.INDEX_LOG4J_MARKER_COL_NAME:
      return event.getProperty(ChainsawConstants.LOG4J_MARKER_COL_NAME_LOWERCASE);

    case ChainsawColumns.INDEX_MILLIS_DELTA_COL_NAME:
      return event.getProperty(ChainsawConstants.MILLIS_DELTA_COL_NAME_LOWERCASE);

    case ChainsawColumns.INDEX_LOGGER_COL_NAME:
      return event.getLoggerName();

    case ChainsawColumns.INDEX_TIMESTAMP_COL_NAME:
      return new Date(event.getTimeStamp());

    case ChainsawColumns.INDEX_MESSAGE_COL_NAME:
      return event.getRenderedMessage();

    case ChainsawColumns.INDEX_NDC_COL_NAME:
      return event.getNDC();

    case ChainsawColumns.INDEX_THREAD_COL_NAME:
      return event.getThreadName();

    case ChainsawColumns.INDEX_THROWABLE_COL_NAME:
      return event.getThrowableStrRep();

    case ChainsawColumns.INDEX_CLASS_COL_NAME:
      return ((info == null) || ("?".equals(info.getClassName()))) ? "" : info.getClassName();

        case ChainsawColumns.INDEX_FILE_COL_NAME:
      return ((info == null) || ("?".equals(info.getFileName()))) ? "" : info.getFileName();

        case ChainsawColumns.INDEX_LINE_COL_NAME:
      return ((info == null) || ("?".equals(info.getLineNumber()))) ? "" : info.getLineNumber();

        case ChainsawColumns.INDEX_METHOD_COL_NAME:
      return ((info == null) || ("?".equals(info.getMethodName()))) ? "" : info.getMethodName();

        default:

            if (columnIndex < columnNames.size()) {
        //case may not match..try case sensitive and fall back to case-insensitive
        String result = event.getProperty(columnNames.get(columnIndex).toString());
        if (result == null) {
            String lowerColName = columnNames.get(columnIndex).toString().toLowerCase();
            Set entrySet = event.getProperties().entrySet();
            for (Iterator iter = entrySet.iterator();iter.hasNext();) {
                Map.Entry thisEntry = (Map.Entry) iter.next();
                if (thisEntry.getKey().toString().toLowerCase().equals(lowerColName)) {
                    result = thisEntry.getValue().toString();
                }
            }
        }
        if (result != null) {
            return result;
        }
      }
    }
    return "";
  }

  public boolean isAddRow(ExtendedLoggingEvent e) {
    e.updateColorRuleColors(colorizer.getBackgroundColor(e), colorizer.getForegroundColor(e));
    Rule findRule = colorizer.getFindRule();
    if (findRule != null) {
      e.evaluateSearchRule(colorizer.getFindRule());
    }

    boolean rowAdded = false;

    Object id = e.getProperty(Constants.LOG4J_ID_KEY);

    if (id == null) {
      id = new Integer(++uniqueRow);
      e.setProperty(Constants.LOG4J_ID_KEY, id.toString());
    }

    /**
         * If we're in cyclic mode and over budget on the size, the addition of a new event will
         * cause the oldest event to fall off the cliff. We need to remove that events ID from the
         * Set so we are not keeping track of IDs for all events ever received (we'd run out of
         * memory...)
         */
    synchronized(mutex) {
        if (cyclic) {
            CyclicBufferList bufferList = (CyclicBufferList) unfilteredList;
            if (bufferList.size() == bufferList.getMaxSize()) {
                reachedCapacity = true;
            }
        }
        int unfilteredSize = unfilteredList.size();
        LoggingEvent lastEvent = null;
        if (unfilteredSize > 0) {
            lastEvent = (LoggingEvent) unfilteredList.get(unfilteredSize - 1);
        }
        unfilteredList.add(e);
        if ((displayRule == null) || (displayRule.evaluate(e, null))) {
            e.setDisplayed(true);
            updateEventMillisDelta(e, lastEvent);
            filteredList.add(e);
            rowAdded = true;
        } else {
            e.setDisplayed(false);
        }
    }

    checkForNewColumn(e);

    return rowAdded;
  }

    private void updateEventMillisDelta(ExtendedLoggingEvent e, LoggingEvent lastEvent) {
      if (lastEvent != null) {
        e.setPreviousDisplayedEventTimestamp(lastEvent.getTimeStamp());
      } else {
        //delta to same event = 0
        e.setPreviousDisplayedEventTimestamp(e.getTimeStamp());
      }
    }

   private void checkForNewColumn(ExtendedLoggingEvent e)
   {
      /**
       * Is this a new Property key we haven't seen before?  Remember that now MDC has been merged
       * into the Properties collection
       */
      boolean newColumn = uniquePropertyKeys.addAll(e.getPropertyKeySet());

      if (newColumn) {
        /**
         * If so, we should add them as columns and notify listeners.
         */
        for (Iterator iter = e.getPropertyKeySet().iterator(); iter.hasNext();) {
          String key = iter.next().toString().toUpperCase();

          //add all keys except the 'log4jid' key (columnNames is all-caps)
          if (!columnNames.contains(key) && !(Constants.LOG4J_ID_KEY.equalsIgnoreCase(key))) {
            columnNames.add(key);
            logger.debug("Adding col '" + key + "', columnNames=" + columnNames);
            fireNewKeyColumnAdded(
              new NewKeyEvent(
                this, columnNames.indexOf(key), key, e.getProperty(key)));
          }
        }
      }
   }

  public void fireTableEvent(final int begin, final int end, final int count) {
  	SwingHelper.invokeOnEDT(new Runnable() {
  		public void run() {
    if (cyclic) {
      if (!reachedCapacity) {
        //if we didn't loop and it's the 1st time, insert
        if ((begin + count) < cyclicBufferSize) {
          fireTableRowsInserted(begin, end);
        } else {
          //we did loop - insert and then update rows
          //rows are zero-indexed, subtract 1 from cyclicbuffersize for the event notification
          fireTableRowsInserted(begin, cyclicBufferSize - 1);
          fireTableRowsUpdated(0, cyclicBufferSize - 1);
          reachedCapacity = true;
        }
      } else {
        fireTableRowsUpdated(0, cyclicBufferSize - 1);
      }
    } else {
      fireTableRowsInserted(begin, end);
    }
  }});
  }

    public void fireRowUpdated(int row, boolean checkForNewColumns) {
        ExtendedLoggingEvent event = getRow(row);
        if (event != null)
        {
            event.updateColorRuleColors(colorizer.getBackgroundColor(event), colorizer.getForegroundColor(event));
            Rule findRule = colorizer.getFindRule();
            if (findRule != null) {
              event.evaluateSearchRule(colorizer.getFindRule());
            }

            fireTableRowsUpdated(row, row);
            if (checkForNewColumns) {
                //row may have had a column added..if so, make sure a column is added
                checkForNewColumn(event);
            }
        }
    }

    /**
  * @param e
  */
  private void fireNewKeyColumnAdded(NewKeyEvent e) {
    NewKeyListener[] listeners =
      (NewKeyListener[]) eventListenerList.getListeners(NewKeyListener.class);

    for (int i = 0; i < listeners.length; i++) {
      NewKeyListener listener = listeners[i];
      listener.newKeyAdded(e);
    }
  }

  /**
   * @return
   */
  public int getMaxSize() {
    return cyclicBufferSize;
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#addNewKeyListener(org.apache.log4j.chainsaw.NewKeyListener)
   */
  public void addNewKeyListener(NewKeyListener l) {
    eventListenerList.add(NewKeyListener.class, l);
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#removeNewKeyListener(org.apache.log4j.chainsaw.NewKeyListener)
   */
  public void removeNewKeyListener(NewKeyListener l) {
    eventListenerList.remove(NewKeyListener.class, l);
  }

  /* (non-Javadoc)
   * @see javax.swing.table.TableModel#isCellEditable(int, int)
   */
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    if (getColumnName(columnIndex).toLowerCase().equals(ChainsawConstants.LOG4J_MARKER_COL_NAME_LOWERCASE)) {
      return true;
    }

    if (columnIndex >= columnNames.size()) {
        return false;
    }

    return super.isCellEditable(rowIndex, columnIndex);
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#setCyclic(boolean)
   */
  public void setCyclic(final boolean cyclic) {
    if (this.cyclic == cyclic) {
      return;
    }

    final boolean old = this.cyclic;
    this.cyclic = cyclic;
    propertySupport.firePropertyChange("cyclic", old, cyclic);
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#addPropertyChangeListener(java.beans.PropertyChangeListener)
   */
  public void addPropertyChangeListener(PropertyChangeListener l) {
    propertySupport.addPropertyChangeListener(l);
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#addPropertyChangeListener(java.lang.String, java.beans.PropertyChangeListener)
   */
  public void addPropertyChangeListener(
    String propertyName, PropertyChangeListener l) {
    propertySupport.addPropertyChangeListener(propertyName, l);
  }

  /* (non-Javadoc)
   * @see org.apache.log4j.chainsaw.EventContainer#size()
   */
  public int size() {
    synchronized(mutex) {
      return unfilteredList.size();
    }
  }

  private class ModelChanger implements PropertyChangeListener {
    /* (non-Javadoc)
     * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
     */
    public void propertyChange(PropertyChangeEvent arg0) {
      Thread thread =
        new Thread(
          new Runnable() {
            public void run() {
              ProgressMonitor monitor = null;

              int index = 0;

              try {
                synchronized (mutex) {
                  monitor =
                    new ProgressMonitor(
                      null, "Switching models...",
                      "Transferring between data structures, please wait...", 0,
                      unfilteredList.size() + 1);
                  monitor.setMillisToDecideToPopup(250);
                  monitor.setMillisToPopup(100);
                  logger.debug(
                    "Changing Model, isCyclic is now " + cyclic);

                  List newUnfilteredList = null;
                  List newFilteredList = null;

                  if (cyclic) {
                    newUnfilteredList = new CyclicBufferList(cyclicBufferSize);
                    newFilteredList = new CyclicBufferList(cyclicBufferSize);
                  } else {
                    newUnfilteredList = new ArrayList(cyclicBufferSize);
                    newFilteredList = new ArrayList(cyclicBufferSize);
                  }

                  int increment = 0;

                  for (Iterator iter = unfilteredList.iterator();
                      iter.hasNext();) {
                    LoggingEvent e = (LoggingEvent) iter.next();
                    newUnfilteredList.add(e);

                    Object o =
                      e.getProperty(
                        e.getProperty(Constants.LOG4J_ID_KEY));

                    monitor.setProgress(index++);
                  }

                  unfilteredList = newUnfilteredList;
                  filteredList = newFilteredList;
                }

                monitor.setNote("Refiltering...");
                reFilter();

                monitor.setProgress(index++);
              } finally {
                monitor.close();
              }

              logger.debug("Model Change completed");
            }
          });
      thread.setPriority(Thread.MIN_PRIORITY + 1);
      thread.start();
    }
  }
}
