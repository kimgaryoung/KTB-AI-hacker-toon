ALTER TABLE conversation_files
    ADD COLUMN self_participant_name VARCHAR(100);

ALTER TABLE conversation_files
    ADD COLUMN other_participant_name VARCHAR(100);

CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY,
    conversation_file_id UUID NOT NULL REFERENCES conversation_files(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES relationships(id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sender_name VARCHAR(100) NOT NULL,
    participant_role VARCHAR(20) NOT NULL,
    content VARCHAR(20000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_conversation_message_file_sequence UNIQUE (conversation_file_id, sequence_number)
);

CREATE INDEX idx_conversation_message_file_sequence
    ON conversation_messages(conversation_file_id, sequence_number);

CREATE INDEX idx_conversation_message_relationship
    ON conversation_messages(relationship_id);
