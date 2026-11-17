update deposit_claims
set status = 'PENDING'
where status in ('AWAITING_VERIFICATION', 'VERIFIED', 'VERIFICATION_EXCEPTION');
