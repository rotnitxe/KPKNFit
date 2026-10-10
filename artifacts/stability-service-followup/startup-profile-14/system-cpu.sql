SELECT p.name,ROUND(SUM(s.dur)/1e6,2) AS running_ms FROM sched s JOIN thread t USING(utid) JOIN process p USING(upid) WHERE s.dur>0 GROUP BY p.upid ORDER BY running_ms DESC LIMIT 15;
