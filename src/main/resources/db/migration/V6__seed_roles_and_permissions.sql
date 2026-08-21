INSERT INTO roles (name) VALUES
    ('ADMIN'), ('TRAINER'), ('MEMBER'), ('RECEPTIONIST');

INSERT INTO permissions (code, description) VALUES
    ('MANAGE_SUBSCRIPTIONS', 'Create/modify subscription plans and pricing'),
    ('VIEW_REPORTS', 'View revenue and attendance reports'),
    ('MANAGE_STAFF', 'Add/remove trainers and receptionists'),
    ('MANAGE_OWN_SCHEDULE', 'Trainer manages their own class/PT schedule'),
    ('CHECK_IN_MEMBER', 'Check a member in at the front desk'),
    ('BOOK_CLASS', 'Member books a class'),
    ('FREEZE_OWN_SUBSCRIPTION', 'Member freezes/unfreezes their own subscription'),
    ('RENEW_SUBSCRIPTION', 'Receptionist renews a member subscription');

-- ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'ADMIN'), id FROM permissions;

-- TRAINER
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'TRAINER'), id
FROM permissions WHERE code IN ('MANAGE_OWN_SCHEDULE', 'CHECK_IN_MEMBER');

-- MEMBER
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'MEMBER'), id
FROM permissions WHERE code IN ('BOOK_CLASS', 'FREEZE_OWN_SUBSCRIPTION');

-- RECEPTIONIST
INSERT INTO role_permissions (role_id, permission_id)
SELECT (SELECT id FROM roles WHERE name = 'RECEPTIONIST'), id
FROM permissions WHERE code IN ('CHECK_IN_MEMBER', 'RENEW_SUBSCRIPTION');
