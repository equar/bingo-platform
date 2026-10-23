ALTER TABLE bingo_agents
    ADD CONSTRAINT uk_bingo_agents_identity_document
        UNIQUE (id_document_type, id_document_number);
