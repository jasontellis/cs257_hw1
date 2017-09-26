package bufmgr;

import global.AbstractBufMgrFrameDesc;
import global.GlobalConst;
import global.PageId;

public class BufMgrFrameDesc extends global.AbstractBufMgrFrameDesc implements GlobalConst
{
	private PageId frame_pageId = null;
	private int pinCount = 0;//JT+
	private boolean dirtyFlag = false;//JT+
	private boolean isEmpty = true;
	
	/**
	 * Returns the pin count of a certain frame page.
	 * 
	 * @return the pin count number.
	 */
	public int getPinCount()
	{ return pinCount; };

	/**
	 * Increments the pin count of a certain frame page when the page is pinned.
	 * 
	 * @return the incremented pin count.
	 */
	public int pin()
	{ 
		return ++pinCount;//JT 
	};

	/**
	 * Decrements the pin count of a frame when the page is unpinned. If the pin
	 * count is equal to or less than zero, the pin count will be zero.
	 * 
	 * @return the decremented pin count.
	 */
	public int unpin()
	{ 
		pinCount = pinCount <= 0 ? 0: --pinCount;//JT+ 
		return pinCount;
	};

	/**
	 * 
	 */
	public PageId getPageNo()
	{ 
		return frame_pageId; 
	};

	/**
	 * the dirty bit, 1 (TRUE) stands for this frame is altered, 0 (FALSE) for
	 * clean frames.
	 */
	public boolean isDirty()
	{ 
		return true; 
	};
	
	public void setDirty() {
		dirtyFlag = true;
	}
	
	public void setPage(PageId pageId) {
		frame_pageId = pageId;
		pin();
	}
	

	public void clearFrame() {
		frame_pageId = null;
		pinCount = 0;
	}
	
	public boolean isEmpty() {
		return frame_pageId == null? true: false;
	}
	
}
