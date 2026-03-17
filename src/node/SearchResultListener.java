package node;

import java.util.List;
import search.FileSearchResult;

public interface SearchResultListener {
    void onSearchResultsReceived(List<FileSearchResult> results);
}
