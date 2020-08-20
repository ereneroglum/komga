CREATE TABLE READLIST
(
    ID                 varchar  NOT NULL PRIMARY KEY,
    NAME               varchar  NOT NULL,
    BOOK_COUNT         int      NOT NULL,
    CREATED_DATE       datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    LAST_MODIFIED_DATE datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE READLIST_BOOK
(
    READLIST_ID varchar NOT NULL,
    BOOK_ID     varchar NOT NULL,
    NUMBER      int     NOT NULL,
    PRIMARY KEY (READLIST_ID, BOOK_ID),
    FOREIGN KEY (READLIST_ID) REFERENCES READLIST (ID),
    FOREIGN KEY (BOOK_ID) REFERENCES BOOK (ID)
);

alter table library
    add column IMPORT_COMICINFO_READLIST boolean NOT NULL DEFAULT 1;
