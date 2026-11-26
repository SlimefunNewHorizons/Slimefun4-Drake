# Guide search pagination

The legacy guide stops after 35 matching items, so broad searches can hide valid matches without reporting an error. With `guide.search-pagination: true`, search results use all 36 content slots and provide previous/next buttons in the bottom row. Page navigation preserves the query and does not append history entries. Toggling a bookmark keeps the current search page.

The setting defaults to `false`, retaining the previous layout and result limit. To roll back, set it to `false` and restart Slimefun with the server; do not use a plugin hot reload. Hidden items and inaccessible item groups retain their existing filtering rules. Cheat mode retains its existing access and item-claim checks.

The regression creates 73 matching items, checks all three pages without duplicates, checks previous/next boundaries and history size, and verifies the legacy layout with the setting disabled. Runtime and deployment evidence belongs in the operational ticket; compilation alone does not establish production compatibility.
