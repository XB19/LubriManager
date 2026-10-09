-- LubriManager — rebaptise les entrées d'audit historiques SALE_* en PURCHASE_*
-- (même concept, renommé lors de la simplification du périmètre, Phase 11).
-- INVENTORY_MOVEMENT n'a pas d'équivalent moderne : conservé tel quel, l'énumération
-- Java le garde comme valeur historique dépréciée (cf. AuditAction).

update audit_log set action = 'PURCHASE_COMPLETED' where action = 'SALE_COMPLETED';
update audit_log set action = 'PURCHASE_REFUSED' where action = 'SALE_REFUSED';
