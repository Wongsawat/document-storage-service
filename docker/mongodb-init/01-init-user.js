// MongoDB initialization script for document-storage-service
// This script creates the database and user with appropriate permissions

db = db.getSiblingDB('document_storage');

// Create user with read/write permissions
db.createUser({
  user: 'docuser',
  pwd: 'docuser123',
  roles: [
    {
      role: 'readWrite',
      db: 'document_storage'
    }
  ]
});

// Create collections with validation
db.createCollection('documents', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['fileName', 'originalFileName', 'contentType', 'fileSize', 'storagePath', 'checksum', 'documentType', 'createdAt'],
      properties: {
        fileName: {
          bsonType: 'string',
          description: 'Unique file name (UUID-based)'
        },
        originalFileName: {
          bsonType: 'string',
          description: 'Original file name from upload'
        },
        contentType: {
          bsonType: 'string',
          description: 'MIME type of the document'
        },
        fileSize: {
          bsonType: 'long',
          minimum: 0,
          description: 'File size in bytes'
        },
        storagePath: {
          bsonType: 'string',
          description: 'Path in storage backend'
        },
        storageUrl: {
          bsonType: 'string',
          description: 'URL to access the document'
        },
        checksum: {
          bsonType: 'string',
          description: 'SHA-256 checksum for integrity verification'
        },
        documentType: {
          bsonType: 'string',
          enum: ['GENERATED_INVOICE', 'MANUAL_UPLOAD', 'XML_ATTACHMENT', 'OTHER'],
          description: 'Type of document'
        },
        invoiceId: {
          bsonType: 'string',
          description: 'Associated invoice ID (UUID)'
        },
        invoiceNumber: {
          bsonType: 'string',
          description: 'Associated invoice number'
        },
        createdAt: {
          bsonType: 'date',
          description: 'Document creation timestamp'
        },
        updatedAt: {
          bsonType: 'date',
          description: 'Document update timestamp'
        },
        expiresAt: {
          bsonType: 'date',
          description: 'Document expiration timestamp (optional)'
        }
      }
    }
  }
});

// Create indexes for performance
db.documents.createIndex({ fileName: 1 }, { unique: true });
db.documents.createIndex({ documentType: 1 });
db.documents.createIndex({ invoiceId: 1 });
db.documents.createIndex({ invoiceNumber: 1 });
db.documents.createIndex({ createdAt: -1 });
db.documents.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0, sparse: true });

// Compound index for common queries
db.documents.createIndex({ invoiceId: 1, documentType: 1, createdAt: -1 });
db.documents.createIndex({ invoiceNumber: 1, createdAt: -1 });

print('✓ Database "document_storage" initialized successfully');
print('✓ User "docuser" created');
print('✓ Collection "documents" created with validation schema');
print('✓ Indexes created for optimized queries');