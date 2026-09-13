-- Front-desk check-in belongs to the RECEPTIONIST role only.
-- TRAINER keeps MANAGE_OWN_SCHEDULE (own class/PT schedule) but no longer
-- checks members in at the front desk.
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'TRAINER')
  AND permission_id = (SELECT id FROM permissions WHERE code = 'CHECK_IN_MEMBER');