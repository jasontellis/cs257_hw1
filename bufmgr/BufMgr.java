/*  File BufMgr,java */

package bufmgr;


import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import global.AbstractBufMgr;
import global.AbstractBufMgrFrameDesc;
import global.PageId;
import global.SystemDefs;
import diskmgr.Page;

import exceptions.BufMgrException;
import exceptions.BufferPoolExceededException;
import exceptions.DiskMgrException;
import exceptions.FileIOException;
import exceptions.HashEntryNotFoundException;
import exceptions.HashOperationException;
import exceptions.InvalidBufferException;
import exceptions.InvalidFrameNumberException;
import exceptions.InvalidPageNumberException;
import exceptions.InvalidReplacerException;
import exceptions.InvalidRunSizeException;
import exceptions.PageNotFoundException;
import exceptions.PageNotReadException;
import exceptions.PagePinnedException;
import exceptions.PageUnpinnedException;
import exceptions.ReplacerException;

// *****************************************************

/**
 * This is a dummy buffer manager class. You will need to replace it with a
 * buffer manager that reads from and writes to disk
 *
 * algorithm to replace the page.
 */
public class BufMgr extends AbstractBufMgr {
	// Replacement policies to be implemented
	public static final String Clock = "Clock";
	public static final String LRU = "LRU";
	public static final String MRU = "MRU";

	// Total number of buffer frames in the buffer pool. */
	private int numBuffers;

	// This buffer manager keeps all pages in memory!
	// private Hashtable pageIdToPageData = new Hashtable();

	// This Hashtable is the buffer pool
	private Hashtable<PageId, byte[]> pageIdToPageData = new Hashtable<PageId, byte[]>();

	// An array of Descriptors one per frame.
	private BufMgrFrameDesc[] frameTable = new BufMgrFrameDesc[NUMBUF];

	/**
	 * Create a buffer manager object.
	 * 
	 * @param numbufs
	 *            number of buffers in the buffer pool.
	 * @param replacerArg
	 *            name of the buffer replacement policy (e.g. BufMgr.Clock).
	 * @throws InvalidReplacerException
	 */
	public BufMgr(int numbufs, String replacerArg) throws InvalidReplacerException {
		numBuffers = numbufs;
		setReplacer(replacerArg);
	}

	/**
	 * Default Constructor Create a buffer manager object.
	 * 
	 * @throws InvalidReplacerException
	 */
	public BufMgr() throws InvalidReplacerException {
		numBuffers = 1;
		replacer = new Clock(this);
	}

	/**
	 * Check if this page is in buffer pool, otherwise find a frame for this page,
	 * read in and pin it. Also write out the old page if it's dirty before reading.
	 * If emptyPage==TRUE, then actually no read is done to bring the page in.
	 * 
	 * @param pin_pgid
	 *            page number in the minibase.
	 * @param page
	 *            the pointer poit to the page.
	 * @param emptyPage
	 *            true (empty page); false (non-empty page)
	 * 
	 * @exception ReplacerException
	 *                if there is a replacer error.
	 * @exception HashOperationException
	 *                if there is a hashtable error.
	 * @exception PageUnpinnedException
	 *                if there is a page that is already unpinned.
	 * @exception InvalidFrameNumberException
	 *                if there is an invalid frame number .
	 * @exception PageNotReadException
	 *                if a page cannot be read.
	 * @exception BufferPoolExceededException
	 *                if the buffer pool is full.
	 * @exception PagePinnedException
	 *                if a page is left pinned .
	 * @exception BufMgrException
	 *                other error occured in bufmgr layer
	 * @exception IOException
	 *                if there is other kinds of I/O error.
	 */

	public void pinPage(PageId pin_pgid, Page page, boolean emptyPage)
			throws ReplacerException, HashOperationException, PageUnpinnedException, InvalidFrameNumberException,
			PageNotReadException, BufferPoolExceededException, PagePinnedException, BufMgrException, IOException 
	
	
	{
		// This buffer manager just keeps allocating new pages and puts them
		// the hash table. It regards each page it is passed as a parameter
		// (<code>page</code> variable) as empty, and thus doesn't take into
		// account any data stored in it.
		//
		// Extend this method to operate as it is supposed to (see the javadoc
		// description above).
		byte[] data = new byte[MAX_SPACE];

		// Check if page in Buff Pool and Pin it
		if (isPageInBuffer(pin_pgid)) {

			int frameForPage = getFrameForPage(pin_pgid);
			if (frameForPage >= 0) {
				frameTable[frameForPage].pin();
			} else {
				throw new BufMgrException(new Exception(),
						"Page found in hash table but not in frame table while pinning.");
			}
		}
		// If Requested Page not in Buffer
		else {
			// Find Empty Frame
			int emptyFrameIndex = getEmptyFrame();

			// if empty frame found, then read page from disk into buffer and pin it
			if (emptyFrameIndex >= 0) {
				try {
					SystemDefs.JavabaseDB.read_page(pin_pgid, page);
				} catch (Exception e) {

				}

				if (!emptyPage) {
					data = page.getpage();

				}
				frameTable[emptyFrameIndex].setPage(pin_pgid);
				pageIdToPageData.put(new PageId(pin_pgid.getPid()), data);

			} else {

				//
				// Find a victim page to replace it with the current one.
				PageId victimPageId;
				Page victimPage;
				BufMgrFrameDesc victimFrame;
				int frameNo = replacer.pick_victim();
				if (frameNo < 0) {
					page = null;
					throw new ReplacerException(null, "BUFMGR: REPLACER_ERROR.");

				}

				victimFrame = frameTable[frameNo];
				victimPageId = victimFrame.getPageNo();
				victimPage = new Page(pageIdToPageData.get(victimPageId));
				if (victimFrame.isDirty()) {
					try {
						SystemDefs.JavabaseDB.write_page(victimPageId, victimPage);
					} catch (Exception e) {
						throw new PageNotReadException(e, "BUFMGR: DB_WRITE_PAGE_ERROR");
					}
				}

				// Remove the victim frame
				victimFrame.clearFrame();
				pageIdToPageData.remove(victimPageId);

				// Read page from disk
				try {
					SystemDefs.JavabaseDB.read_page(pin_pgid, page);
				} catch (Exception e) {
					throw new PageNotReadException(e, "BUFMGR: DB_READ_PAGE_ERROR");
				}
				// Move new frame to Frame table and buffer pool
				victimFrame.setPage(pin_pgid);
				pageIdToPageData.put(pin_pgid, page.getpage());

				try {
					SystemDefs.JavabaseDB.read_page(pin_pgid, page);
				} catch (Exception e) {
					throw new PageNotReadException(e, "BUFMGR: DB_READ_PAGE_ERROR");
				}

//				// The following code excerpt reads the contents of the page with id pin_pgid
//				// into the object page. Use it to read the contents of a dirty page to be
//				// written back to disk.
//				try {
//					SystemDefs.JavabaseDB.read_page(pin_pgid, page);
//				} catch (Exception e) {
//					throw new PageNotReadException(e, "BUFMGR: DB_READ_PAGE_ERROR");
//				}

			}
		}

		// page.setpage(data);

		// Hint: Notice that this naive Buffer Manager allocates a page, but does not
		// associate it with a page frame descriptor (an entry of the frameTable
		// object). Your Buffer Manager shouldn't be that naive ;) . Have in mind that
		// the hashtable is simply the "storage" area of your pages, while the
		// frameTable
		// contains the frame descriptors, each associated with one loaded page, which
		// stores that page's metadata (pin count, status, etc.)

		// Find a victim page to replace it with the current one.
		int frameNo = replacer.pick_victim();
		if (frameNo < 0) {
			page = null;
			throw new ReplacerException(null, "BUFMGR: REPLACER_ERROR.");

		}

		// The following code excerpt reads the contents of the page with id pin_pgid
		// into the object page. Use it to read the contents of a dirty page to be
		// written back to disk.
		try {
			SystemDefs.JavabaseDB.read_page(pin_pgid, page);
		} catch (Exception e) {
			throw new PageNotReadException(e, "BUFMGR: DB_READ_PAGE_ERROR");
		}
	}

	private boolean isPageInBuffer(PageId pageId) {
		return pageIdToPageData.containsKey(pageId);
	}

	private boolean isBUfferAvailable() {
		boolean bufferAvailable = false;
		bufferAvailable = pageIdToPageData.size() == NUMBUF ? false : true;

		return bufferAvailable;

	}

	private int getEmptyFrame() {
		int emptyFrameIndex = -1;
		for (int i = 0; i < frameTable.length; ++i) {
			if (frameTable[i].isEmpty() == true) {
				emptyFrameIndex = i;
			}

		}
		return emptyFrameIndex;

	}

	private int getFrameForPage(PageId pageId) {
		int frame = -1;
		for (int i = 0; i < frameTable.length; ++i) {
			if (frameTable[i].getPageNo() == pageId) {
				frame = i;
				break;
			}
		}
		return frame;
	}

	/**
	 * To unpin a page specified by a pageId. If pincount>0, decrement it and if it
	 * becomes zero, put it in a group of replacement candidates. if pincount=0
	 * before this call, return error.
	 * 
	 * @param globalPageId_in_a_DB
	 *            page number in the minibase.
	 * @param dirty
	 *            the dirty bit of the frame
	 * 
	 * @exception ReplacerException
	 *                if there is a replacer error.
	 * @exception PageUnpinnedException
	 *                if there is a page that is already unpinned.
	 * @exception InvalidFrameNumberException
	 *                if there is an invalid frame number .
	 * @exception HashEntryNotFoundException
	 *                if there is no entry of page in the hash table.
	 */
	public void unpinPage(PageId PageId_in_a_DB, boolean dirty)
			throws ReplacerException, PageUnpinnedException, HashEntryNotFoundException, InvalidFrameNumberException {
		int pinCount = -1; // setting -1 as init val to prevent "variable might not have been initialized"
							// warning/error
		boolean pageFoundInFrameTable = false;
		for (int i = 0; i <= frameTable.length; i++) {

			if (PageId_in_a_DB.equals(frameTable[i].getPageNo())) {

				pageFoundInFrameTable = true;

				if (frameTable[i].getPinCount() <= 0) {
					throw new PageUnpinnedException(new Exception(),
							"Page you're trying to unpin already have pincount <= 0");
				} else {
					pinCount = frameTable[i].unpin();
					if (dirty) {
						frameTable[i].setDirty();
					}
					if (pinCount == 0) {
						// add to pool of replacement candidates
					}
				}
				break;
			}
		}
		if (!pageFoundInFrameTable) {
			// exception thrown, need to figure which one
			throw new PageUnpinnedException(new Exception(),
					"Page you're trying to unpin is not found in the frameTable");
		}
	}

	/**
	 * Call DB object to allocate a run of new pages and find a frame in the buffer
	 * pool for the first page and pin it. If buffer is full, ask DB to deallocate
	 * all these pages and return error (null if error).
	 * 
	 * @param firstpage
	 *            the address of the first page.
	 * @param howmany
	 *            total number of allocated new pages.
	 * @return the first page id of the new pages.
	 * 
	 * @exception BufferPoolExceededException
	 *                if the buffer pool is full.
	 * @exception HashOperationException
	 *                if there is a hashtable error.
	 * @exception ReplacerException
	 *                if there is a replacer error.
	 * @exception HashEntryNotFoundException
	 *                if there is no entry of page in the hash table.
	 * @exception InvalidFrameNumberException
	 *                if there is an invalid frame number.
	 * @exception PageUnpinnedException
	 *                if there is a page that is already unpinned.
	 * @exception PagePinnedException
	 *                if a page is left pinned.
	 * @exception PageNotReadException
	 *                if a page cannot be read.
	 * @exception IOException
	 *                if there is other kinds of I/O error.
	 * @exception BufMgrException
	 *                other error occured in bufmgr layer
	 * @exception DiskMgrException
	 *                other error occured in diskmgr layer
	 */
	public PageId newPage(Page firstpage, int howmany) throws BufferPoolExceededException, HashOperationException,
			ReplacerException, HashEntryNotFoundException, InvalidFrameNumberException, PagePinnedException,
			PageUnpinnedException, PageNotReadException, BufMgrException, DiskMgrException, IOException {

		PageId pageId = new PageId();

		try {
			SystemDefs.JavabaseDB.allocate_page(pageId, howmany);
		}

		catch (Exception e) {

		}
		return pageId;
	}

	/**
	 * User should call this method if s/he needs to delete a page. this routine
	 * will call DB to deallocate the page.
	 * 
	 * @param globalPageId
	 *            the page number in the data base.
	 * @exception InvalidBufferException
	 *                if buffer pool corrupted.
	 * @exception ReplacerException
	 *                if there is a replacer error.
	 * @exception HashOperationException
	 *                if there is a hash table error.
	 * @exception InvalidFrameNumberException
	 *                if there is an invalid frame number.
	 * @exception PageNotReadException
	 *                if a page cannot be read.
	 * @exception BufferPoolExceededException
	 *                if the buffer pool is already full.
	 * @exception PagePinnedException
	 *                if a page is left pinned.
	 * @exception PageUnpinnedException
	 *                if there is a page that is already unpinned.
	 * @exception HashEntryNotFoundException
	 *                if there is no entry of page in the hash table.
	 * @exception IOException
	 *                if there is other kinds of I/O error.
	 * @exception BufMgrException
	 *                other error occured in bufmgr layer
	 * @exception DiskMgrException
	 *                other error occured in diskmgr layer
	 */
	public void freePage(PageId globalPageId) throws InvalidBufferException,
			ReplacerException, HashOperationException,
			InvalidFrameNumberException, PageNotReadException,
			BufferPoolExceededException, PagePinnedException,
			PageUnpinnedException, HashEntryNotFoundException, BufMgrException,
			DiskMgrException, IOException
	{
		frameTable[getFrameForPage(globalPageId)].clearFrame();
		pageIdToPageData.remove(globalPageId);
		try {
			SystemDefs.JavabaseDB.deallocate_page(globalPageId, 1);
		} catch (InvalidRunSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidPageNumberException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileIOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Added to flush a particular page of the buffer pool to disk
	 * 
	 * @param pageid
	 *            the page number in the database.
	 * 
	 * @exception HashOperationException
	 *                if there is a hashtable error.
	 * @exception PageUnpinnedException
	 *                if there is a page that is already unpinned.
	 * @exception PagePinnedException
	 *                if a page is left pinned.
	 * @exception PageNotFoundException
	 *                if a page is not found.
	 * @exception BufMgrException
	 *                other error occured in bufmgr layer
	 * @exception IOException
	 *                if there is other kinds of I/O error.
	 */
	public void flushPage(PageId pageid) throws HashOperationException,
			PageUnpinnedException, PagePinnedException, PageNotFoundException,
			BufMgrException, IOException
	{
		try {
			SystemDefs.JavabaseDB.write_page(pageid, new Page(pageIdToPageData.get(pageid)));
		} catch (FileIOException | InvalidPageNumberException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Flushes all pages of the buffer pool to disk
	 * 
	 * @exception HashOperationException
	 *                if there is a hashtable error.
	 * @exception PageUnpinnedException
	 *                if there is a page that is already unpinned.
	 * @exception PagePinnedException
	 *                if a page is left pinned.
	 * @exception PageNotFoundException
	 *                if a page is not found.
	 * @exception BufMgrException
	 *                other error occured in bufmgr layer
	 * @exception IOException
	 *                if there is other kinds of I/O error.
	 */
	 
	// question: in what situations would you need this? during exit?
	public void flushAllPages() throws HashOperationException,
			PageUnpinnedException, PagePinnedException, PageNotFoundException,
			BufMgrException, IOException
	{
		for (PageId pageId : pageIdToPageData.keySet()) {
			try {
				SystemDefs.JavabaseDB.write_page(pageId, new Page(pageIdToPageData.get(pageId)));
			} catch (FileIOException | InvalidPageNumberException e) {
				e.printStackTrace();
			}
		}
	}
	

	/**
	 * Gets the total number of buffers.
	 * 
	 * @return total number of buffer frames.
	 */
	public int getNumBuffers() {
		return numBuffers;
	}

	/**
	 * Gets the total number of unpinned buffer frames.
	 * 
	 * @return total number of unpinned buffer frames.
	 */
	public int getNumUnpinnedBuffers() {
		int unpinnedBuffers = 0;
		for (int frameIndex = 0; frameIndex < frameTable.length; ++frameIndex) {
			if (frameTable[frameIndex].getPageNo() == null) {
				++unpinnedBuffers;
			}
		}
		return unpinnedBuffers;
	}

	/** A few routines currently need direct access to the FrameTable. */
	public AbstractBufMgrFrameDesc[] getFrameTable() {
		return this.frameTable;
	}

}
