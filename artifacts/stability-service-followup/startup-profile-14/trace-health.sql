SELECT name,severity,source,value FROM stats WHERE value>0 AND severity IN ('error','data_loss');
